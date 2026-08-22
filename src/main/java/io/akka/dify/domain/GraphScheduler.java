package io.akka.dify.domain;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Runs a {@link Graph} to completion, one ready node at a time (SPEC-001 R12 / §4 OD1),
 * implementing the OR-join readiness rule, skip propagation, retry, and error-strategy
 * fallthrough of SPEC-001 §3.
 */
public final class GraphScheduler {

  private GraphScheduler() {}

  public static RunResult run(Graph graph, Map<String, Object> initialVariables) {
    return run(graph, initialVariables, ExecutionLimits.DEFAULT);
  }

  public static RunResult run(Graph graph, Map<String, Object> initialVariables, ExecutionLimits limits) {
    RunState state = new RunState();
    Map<String, Object> variables = new LinkedHashMap<>(initialVariables);
    long startNanos = System.nanoTime();
    int steps = 0;

    // The root is always ready (SPEC-001 R1); it never goes through isReady().
    state.enqueue(graph.rootNodeId());

    while (state.hasReadyWork()) {
      if (steps >= limits.maxSteps()) {
        return abortedResult(
            state, "Maximum execution steps exceeded: " + steps + " > " + limits.maxSteps());
      }
      long elapsedMillis = (System.nanoTime() - startNanos) / 1_000_000;
      if (elapsedMillis > limits.maxTimeMillis()) {
        return abortedResult(
            state,
            "Maximum execution time exceeded: " + elapsedMillis + "ms > " + limits.maxTimeMillis() + "ms");
      }

      String nodeId = state.dequeue();
      GraphNode node = graph.node(nodeId);
      steps++;
      state.events.add(new GraphEvent.NodeStarted(nodeId));

      NodeOutcome outcome = runWithRetry(node, variables, state);

      if (outcome instanceof NodeOutcome.Failure failure) {
        // Only ErrorStrategy.ABORT with no retry left reaches here (SPEC-001 R9).
        return failedResult(state, failure.errorMessage());
      }

      NodeOutcome.Success success = (NodeOutcome.Success) outcome;
      state.outputsByNode.put(nodeId, success.outputs());
      for (Map.Entry<String, Object> entry : success.outputs().entrySet()) {
        variables.put(nodeId + "." + entry.getKey(), entry.getValue());
      }
      state.events.add(new GraphEvent.NodeSucceeded(nodeId, success.outputs()));

      if (success.selectedHandle() != null) {
        completeBranch(graph, state, nodeId, success.selectedHandle());
      } else {
        completeNonBranch(graph, state, nodeId);
      }
    }

    RunOutcome outcome = state.exceptionsCount > 0 ? RunOutcome.PARTIAL_SUCCEEDED : RunOutcome.SUCCEEDED;
    return new RunResult(outcome, state.outputsByNode, state.events, state.exceptionsCount, null);
  }

  /**
   * Runs a node, retrying on failure while its retry policy allows (SPEC-001 R6-R7), then
   * applying its error strategy once retry no longer applies (R9). Returns a {@code Success}
   * for every outcome except a terminal {@code ABORT} failure.
   */
  private static NodeOutcome runWithRetry(GraphNode node, Map<String, Object> variables, RunState state) {
    int retryCount = 0;
    while (true) {
      NodeOutcome outcome = node.behavior().run(variables);
      if (outcome instanceof NodeOutcome.Success) {
        return outcome;
      }

      NodeOutcome.Failure failure = (NodeOutcome.Failure) outcome;
      state.exceptionsCount++; // SPEC-001 R8: every failed attempt counts, even a retried one.
      state.events.add(new GraphEvent.NodeFailed(node.id(), failure.errorMessage()));

      RetryConfig retryConfig = node.retryConfig();
      if (retryConfig.retryEnabled() && retryCount < retryConfig.maxRetries()) {
        int retryIndex = retryCount + 1;
        state.events.add(new GraphEvent.NodeRetried(node.id(), retryIndex));
        sleepFor(retryConfig.retryIntervalMillis()); // SPEC-001 §4 OD2: blocks, like the source.
        retryCount = retryIndex;
        continue;
      }

      return applyErrorStrategy(node, failure);
    }
  }

  private static NodeOutcome applyErrorStrategy(GraphNode node, NodeOutcome.Failure failure) {
    return switch (node.errorStrategy()) {
      case ABORT -> failure;
      case FAIL_BRANCH ->
          NodeOutcome.success(
              Map.of("error_message", failure.errorMessage(), "error_type", "WorkflowNodeError"),
              "fail-branch");
      case DEFAULT_VALUE -> {
        Map<String, Object> merged = new LinkedHashMap<>(node.defaultValues());
        merged.put("error_message", failure.errorMessage());
        merged.put("error_type", "WorkflowNodeError");
        yield NodeOutcome.success(merged);
      }
    };
  }

  private static void sleepFor(long millis) {
    if (millis <= 0) {
      return;
    }
    try {
      Thread.sleep(millis);
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      throw new IllegalStateException("Interrupted while waiting to retry", e);
    }
  }

  /** SPEC-001 R4: selected-handle edges are taken, every other outgoing edge is skipped. */
  private static void completeBranch(Graph graph, RunState state, String nodeId, String selectedHandle) {
    for (Edge edge : graph.outgoing(nodeId)) {
      if (edge.sourceHandle().equals(selectedHandle)) {
        takeEdge(graph, state, edge);
      } else {
        skipEdgePath(graph, state, edge);
      }
    }
  }

  /** SPEC-001 R3: every outgoing edge of a non-branch success is taken. */
  private static void completeNonBranch(Graph graph, RunState state, String nodeId) {
    for (Edge edge : graph.outgoing(nodeId)) {
      takeEdge(graph, state, edge);
    }
  }

  private static void takeEdge(Graph graph, RunState state, Edge edge) {
    state.setEdgeState(edge.id(), NodeState.TAKEN);
    state.events.add(new GraphEvent.EdgeTaken(edge.id(), edge.tail(), edge.head(), edge.sourceHandle()));
    if (isReady(graph, state, edge.head())) {
      state.enqueue(edge.head());
    }
  }

  private static void skipEdgePath(Graph graph, RunState state, Edge edge) {
    state.setEdgeState(edge.id(), NodeState.SKIPPED);
    state.events.add(new GraphEvent.EdgeSkipped(edge.id(), edge.tail(), edge.head(), edge.sourceHandle()));
    propagateSkipFromEdge(graph, state, edge);
  }

  /** SPEC-001 R5: recursively propagate a skip through the downstream head of a skipped edge. */
  private static void propagateSkipFromEdge(Graph graph, RunState state, Edge skippedEdge) {
    String downstream = skippedEdge.head();
    List<Edge> incoming = graph.incoming(downstream);

    boolean hasUnknown = incoming.stream().anyMatch(e -> state.edgeState(e.id()) == NodeState.UNKNOWN);
    if (hasUnknown) {
      return; // deferred until the still-unresolved edge resolves
    }
    boolean hasTaken = incoming.stream().anyMatch(e -> state.edgeState(e.id()) == NodeState.TAKEN);
    if (hasTaken) {
      state.enqueue(downstream); // ready some other way (rule 2)
      return;
    }
    propagateSkipToNode(graph, state, downstream); // every incoming edge is SKIPPED
  }

  private static void propagateSkipToNode(Graph graph, RunState state, String nodeId) {
    state.setNodeState(nodeId, NodeState.SKIPPED);
    for (Edge edge : graph.outgoing(nodeId)) {
      state.setEdgeState(edge.id(), NodeState.SKIPPED);
      state.events.add(new GraphEvent.EdgeSkipped(edge.id(), edge.tail(), edge.head(), edge.sourceHandle()));
      propagateSkipFromEdge(graph, state, edge);
    }
  }

  /** SPEC-001 R1-R2: the OR-join readiness rule. */
  private static boolean isReady(Graph graph, RunState state, String nodeId) {
    List<Edge> incoming = graph.incoming(nodeId);
    if (incoming.isEmpty()) {
      return true;
    }
    boolean hasUnknown = incoming.stream().anyMatch(e -> state.edgeState(e.id()) == NodeState.UNKNOWN);
    if (hasUnknown) {
      return false;
    }
    return incoming.stream().anyMatch(e -> state.edgeState(e.id()) == NodeState.TAKEN);
  }

  private static RunResult failedResult(RunState state, String reason) {
    return new RunResult(
        RunOutcome.FAILED, state.outputsByNode, state.events, state.exceptionsCount, reason);
  }

  private static RunResult abortedResult(RunState state, String reason) {
    return new RunResult(
        RunOutcome.ABORTED, state.outputsByNode, state.events, state.exceptionsCount, reason);
  }
}
