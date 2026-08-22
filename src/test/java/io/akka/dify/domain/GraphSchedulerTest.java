package io.akka.dify.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * Exercises SPEC-001's rules R1-R11 the same shapes {@code dify-port/probes/graphon_probe.py}
 * exercised the real {@code graphon} 0.7.0 engine with — same assertions, this time against
 * the port.
 */
class GraphSchedulerTest {

  private static GraphNode task(String id, NodeBehavior behavior) {
    return GraphNode.task(id, behavior);
  }

  private static GraphNode succeeding(String id) {
    return task(id, v -> NodeOutcome.success(Map.of()));
  }

  // start -> branch -> {true: a, false: b} -> join -> end
  private static List<GraphNode> diamond(NodeBehavior branchBehavior, NodeBehavior bBehavior) {
    return List.of(
        succeeding("start"),
        GraphNode.branch("branch", branchBehavior),
        succeeding("a"),
        task("b", bBehavior),
        succeeding("join"),
        succeeding("end"));
  }

  private static List<Edge> diamondEdges() {
    return List.of(
        new Edge("e0", "start", "branch"),
        new Edge("e1", "branch", "a", "true"),
        new Edge("e2", "branch", "b", "false"),
        new Edge("e3", "a", "join"),
        new Edge("e4", "b", "join"),
        new Edge("e5", "join", "end"));
  }

  @Test
  void orJoinRunsOnceEvenWhenOneBranchIsSkipped() {
    Graph graph =
        new Graph(
            diamond(v -> NodeOutcome.success(Map.of(), "true"), v -> NodeOutcome.success(Map.of())),
            diamondEdges(),
            "start");

    RunResult result = GraphScheduler.run(graph, Map.of());

    assertThat(result.outcome()).isEqualTo(RunOutcome.SUCCEEDED);
    assertThat(startedNodeIds(result)).containsExactly("start", "branch", "a", "join", "end");
    assertThat(startedNodeIds(result)).doesNotContain("b");
  }

  @Test
  void branchTakesSelectedHandleAndSkipsTheOther() {
    Graph graph =
        new Graph(
            diamond(v -> NodeOutcome.success(Map.of(), "true"), v -> NodeOutcome.success(Map.of())),
            diamondEdges(),
            "start");

    RunResult result = GraphScheduler.run(graph, Map.of());

    assertThat(takenEdgeIds(result)).contains("e1").doesNotContain("e2");
    assertThat(skippedEdgeIds(result)).contains("e2").doesNotContain("e1");
  }

  @Test
  void skipPropagatesThroughWhollySkippedChain() {
    // start -> branch -> {true: a, false: b1 -> b2} -> join -> end
    List<GraphNode> nodes =
        List.of(
            succeeding("start"),
            GraphNode.branch("branch", v -> NodeOutcome.success(Map.of(), "true")),
            succeeding("a"),
            succeeding("b1"),
            succeeding("b2"),
            succeeding("join"),
            succeeding("end"));
    List<Edge> edges =
        List.of(
            new Edge("e0", "start", "branch"),
            new Edge("e1", "branch", "a", "true"),
            new Edge("e2", "branch", "b1", "false"),
            new Edge("e3", "b1", "b2"),
            new Edge("e4", "a", "join"),
            new Edge("e5", "b2", "join"),
            new Edge("e6", "join", "end"));
    Graph graph = new Graph(nodes, edges, "start");

    RunResult result = GraphScheduler.run(graph, Map.of());

    assertThat(startedNodeIds(result)).doesNotContain("b1", "b2");
    assertThat(skippedEdgeIds(result)).contains("e2", "e3");
    assertThat(startedNodeIds(result)).contains("join", "end");
  }

  @Test
  void skipStopsAtNodeWithUnknownIncomingEdge() {
    // root -> branch1 -> {true: t1, false: f1}
    // root -> branch2 -> {true: t2, false: f2}
    // f1, f2 -> gate
    // root fires both branches (non-branch success takes every outgoing edge); the scheduler
    // dispatches branch1 before branch2 (FIFO, SPEC-001 R12), so when branch1 selects "true"
    // and skips e-f1, gate's other incoming edge (from branch2) is still UNKNOWN and skip
    // propagation must stop rather than skipping gate outright.
    List<GraphNode> nodes =
        List.of(
            succeeding("root"),
            GraphNode.branch("branch1", v -> NodeOutcome.success(Map.of(), "true")),
            succeeding("t1"),
            succeeding("f1"),
            GraphNode.branch("branch2", v -> NodeOutcome.success(Map.of(), "false")),
            succeeding("t2"),
            succeeding("f2"),
            succeeding("gate"),
            succeeding("afterGate"));
    List<Edge> edges =
        List.of(
            new Edge("e0", "root", "branch1"),
            new Edge("e1", "root", "branch2"),
            new Edge("e2", "branch1", "t1", "true"),
            new Edge("e3", "branch1", "f1", "false"),
            new Edge("e4", "branch2", "t2", "true"),
            new Edge("e5", "branch2", "f2", "false"),
            new Edge("e6", "f1", "gate"),
            new Edge("e7", "f2", "gate"),
            new Edge("e8", "gate", "afterGate"));
    Graph graph = new Graph(nodes, edges, "root");

    RunResult result = GraphScheduler.run(graph, Map.of());

    // branch1 selects "true" (t1 taken, f1 skipped); branch2 selects "false" (f2 taken, t2
    // skipped). gate's only route is through f1/f2, so it becomes ready once branch2 resolves
    // f2 -- despite f1 having been skipped first, leaving gate with one UNKNOWN incoming edge
    // in between. If propagation incorrectly treated that in-between state as "every incoming
    // edge is skipped," it would mark gate SKIPPED and, with it, gate's own outgoing edge to
    // afterGate -- a defect that a topology without any node after gate cannot reveal, since
    // gate still gets dispatched independently once e7 is taken (rule 2), regardless of what
    // its node state was mistakenly set to. afterGate running (and e8 being TAKEN, not
    // SKIPPED) is what actually distinguishes "stopped, deferred" from "wrongly skipped."
    assertThat(startedNodeIds(result)).contains("t1", "f2", "gate", "afterGate");
    assertThat(startedNodeIds(result)).doesNotContain("f1", "t2");
    assertThat(skippedEdgeIds(result)).contains("e3", "e6");
    assertThat(takenEdgeIds(result)).contains("e7", "e8");
    // e8 (gate -> afterGate) must never appear as skipped: it is downstream of gate, not of
    // f1, and gate itself was never legitimately skipped -- only deferred (this is what "stop"
    // actually means, as distinct from "eventually corrected"). A propagation that skipped
    // past the UNKNOWN e7 first and only found out later that e7 resolved TAKEN would still
    // make gate (and afterGate) run in this design -- takeEdge re-marks TAKEN and rechecks
    // readiness unconditionally on the resolving path -- but it would also leave a stray
    // EdgeSkipped(e8) in the trace before the correcting EdgeTaken(e8), which is the
    // observable difference this assertion is for.
    assertThat(skippedEdgeIds(result)).doesNotContain("e8");
  }

  @Test
  void retryDisabledFailsImmediately() {
    GraphNode node =
        task("n", v -> NodeOutcome.failure("boom"))
            .withRetry(new RetryConfig(3, 0, false));
    Graph graph = new Graph(List.of(node), List.of(), "n");

    RunResult result = GraphScheduler.run(graph, Map.of());

    assertThat(result.outcome()).isEqualTo(RunOutcome.FAILED);
    assertThat(retryEvents(result, "n")).isEmpty();
  }

  @Test
  void retryIndexSequenceMatchesSource() {
    GraphNode node =
        task("n", Behaviors.flaky(2, "boom")).withRetry(new RetryConfig(3, 1, true));
    Graph graph = new Graph(List.of(node), List.of(), "n");

    RunResult result = GraphScheduler.run(graph, Map.of());

    assertThat(retryEvents(result, "n")).containsExactly(1, 2);
    assertThat(result.outputsByNode().get("n")).containsEntry("attempt", 3);
  }

  @Test
  void retriedNodeThatEventuallySucceedsIsPartialSucceeded() {
    GraphNode node =
        task("n", Behaviors.flaky(2, "boom")).withRetry(new RetryConfig(3, 1, true));
    Graph graph = new Graph(List.of(node), List.of(), "n");

    RunResult result = GraphScheduler.run(graph, Map.of());

    assertThat(result.outcome()).isEqualTo(RunOutcome.PARTIAL_SUCCEEDED);
    assertThat(result.exceptionsCount()).isEqualTo(2);
  }

  @Test
  void abortStrategyStopsTheRun() {
    GraphNode failing = task("n", Behaviors.alwaysFail("boom"));
    GraphNode unreachableIfSkipped = succeeding("downstream");
    Graph graph =
        new Graph(
            List.of(failing, unreachableIfSkipped),
            List.of(new Edge("e0", "n", "downstream")),
            "n");

    RunResult result = GraphScheduler.run(graph, Map.of());

    assertThat(result.outcome()).isEqualTo(RunOutcome.FAILED);
    assertThat(result.failureReason()).contains("boom");
    assertThat(startedNodeIds(result)).doesNotContain("downstream");
  }

  @Test
  void failBranchRoutesThroughFailBranchHandle() {
    GraphNode failing =
        task("n", Behaviors.alwaysFail("boom")).withErrorStrategy(ErrorStrategy.FAIL_BRANCH);
    Graph graph =
        new Graph(
            List.of(failing, succeeding("normalPath"), succeeding("failPath")),
            List.of(
                new Edge("e0", "n", "normalPath", "source"),
                new Edge("e1", "n", "failPath", "fail-branch")),
            "n");

    RunResult result = GraphScheduler.run(graph, Map.of());

    assertThat(result.outcome()).isEqualTo(RunOutcome.PARTIAL_SUCCEEDED);
    assertThat(startedNodeIds(result)).contains("failPath");
    assertThat(startedNodeIds(result)).doesNotContain("normalPath");
    assertThat(result.outputsByNode().get("n"))
        .containsEntry("error_message", "boom")
        .containsEntry("error_type", "WorkflowNodeError");
  }

  @Test
  void defaultValueStrategyMergesConfiguredDefaults() {
    GraphNode failing =
        task("n", Behaviors.alwaysFail("boom"))
            .withErrorStrategy(ErrorStrategy.DEFAULT_VALUE)
            .withDefaultValues(Map.of("result", "fallback"));
    Graph graph =
        new Graph(
            List.of(failing, succeeding("next")),
            List.of(new Edge("e0", "n", "next")),
            "n");

    RunResult result = GraphScheduler.run(graph, Map.of());

    assertThat(result.outcome()).isEqualTo(RunOutcome.PARTIAL_SUCCEEDED);
    assertThat(startedNodeIds(result)).contains("next");
    assertThat(result.outputsByNode().get("n"))
        .containsEntry("result", "fallback")
        .containsEntry("error_message", "boom")
        .containsEntry("error_type", "WorkflowNodeError");
  }

  @Test
  void stepLimitAbortsTheRun() {
    List<GraphNode> nodes =
        List.of(succeeding("a"), succeeding("b"), succeeding("c"), succeeding("d"), succeeding("e"));
    List<Edge> edges =
        List.of(
            new Edge("e0", "a", "b"),
            new Edge("e1", "b", "c"),
            new Edge("e2", "c", "d"),
            new Edge("e3", "d", "e"));
    Graph graph = new Graph(nodes, edges, "a");

    RunResult result = GraphScheduler.run(graph, Map.of(), new ExecutionLimits(2, 1_200_000L));

    assertThat(result.outcome()).isEqualTo(RunOutcome.ABORTED);
    assertThat(result.failureReason()).contains("steps");
    assertThat(startedNodeIds(result)).containsExactly("a", "b");
  }

  @Test
  void timeLimitAbortsTheRun() {
    GraphNode slow =
        task(
            "slow",
            v -> {
              try {
                Thread.sleep(30);
              } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
              }
              return NodeOutcome.success(Map.of());
            });
    Graph graph =
        new Graph(
            List.of(slow, succeeding("fast")),
            List.of(new Edge("e0", "slow", "fast")),
            "slow");

    RunResult result = GraphScheduler.run(graph, Map.of(), new ExecutionLimits(500, 10L));

    assertThat(result.outcome()).isEqualTo(RunOutcome.ABORTED);
    assertThat(result.failureReason()).contains("time");
    assertThat(startedNodeIds(result)).containsExactly("slow");
  }

  @Test
  void outcomeIsSucceededWithNoFailures() {
    Graph graph = new Graph(List.of(succeeding("only")), List.of(), "only");

    RunResult result = GraphScheduler.run(graph, Map.of());

    assertThat(result.outcome()).isEqualTo(RunOutcome.SUCCEEDED);
    assertThat(result.exceptionsCount()).isZero();
  }

  private static List<String> startedNodeIds(RunResult result) {
    return result.events().stream()
        .filter(GraphEvent.NodeStarted.class::isInstance)
        .map(e -> ((GraphEvent.NodeStarted) e).nodeId())
        .toList();
  }

  private static List<String> takenEdgeIds(RunResult result) {
    return result.events().stream()
        .filter(GraphEvent.EdgeTaken.class::isInstance)
        .map(e -> ((GraphEvent.EdgeTaken) e).edgeId())
        .toList();
  }

  private static List<String> skippedEdgeIds(RunResult result) {
    return result.events().stream()
        .filter(GraphEvent.EdgeSkipped.class::isInstance)
        .map(e -> ((GraphEvent.EdgeSkipped) e).edgeId())
        .toList();
  }

  private static List<Integer> retryEvents(RunResult result, String nodeId) {
    return result.events().stream()
        .filter(GraphEvent.NodeRetried.class::isInstance)
        .map(GraphEvent.NodeRetried.class::cast)
        .filter(e -> e.nodeId().equals(nodeId))
        .map(GraphEvent.NodeRetried::retryIndex)
        .toList();
  }
}
