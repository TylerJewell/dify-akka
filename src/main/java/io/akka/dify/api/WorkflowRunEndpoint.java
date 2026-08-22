package io.akka.dify.api;

import akka.javasdk.annotations.Acl;
import akka.javasdk.annotations.http.HttpEndpoint;
import akka.javasdk.annotations.http.Post;
import akka.javasdk.http.HttpException;
import io.akka.dify.domain.BuiltinNodeType;
import io.akka.dify.domain.Edge;
import io.akka.dify.domain.ExecutionLimits;
import io.akka.dify.domain.ErrorStrategy;
import io.akka.dify.domain.Graph;
import io.akka.dify.domain.GraphEvent;
import io.akka.dify.domain.GraphNode;
import io.akka.dify.domain.GraphScheduler;
import io.akka.dify.domain.RetryConfig;
import io.akka.dify.domain.RunResult;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Runs one workflow graph, described entirely as data (SPEC-001): named nodes built from
 * {@link BuiltinNodeType}, wired by explicit tail/head/handle edges, and executed to
 * completion in a single call. This is the port's reachable surface — the thing an external
 * caller drives, distinct from the unit tests that exercise {@code GraphScheduler} directly.
 */
@Acl(allow = @Acl.Matcher(principal = Acl.Principal.ALL))
@HttpEndpoint("/workflows")
public class WorkflowRunEndpoint {

  public record RetryRequest(Integer maxRetries, Long retryIntervalMillis, Boolean retryEnabled) {}

  public record NodeRequest(
      String id,
      String type,
      Map<String, Object> config,
      RetryRequest retry,
      String errorStrategy,
      Map<String, Object> defaultValues) {}

  public record EdgeRequest(String tail, String head, String sourceHandle) {}

  public record RunRequest(
      List<NodeRequest> nodes,
      List<EdgeRequest> edges,
      String rootNodeId,
      Map<String, Object> initialVariables,
      Integer maxSteps,
      Long maxTimeMillis) {}

  public record RunResponse(
      String outcome,
      Map<String, Map<String, Object>> outputsByNode,
      List<String> trace,
      int exceptionsCount,
      String failureReason) {
    static RunResponse from(RunResult result) {
      return new RunResponse(
          result.outcome().name(),
          result.outputsByNode(),
          describe(result.events()),
          result.exceptionsCount(),
          result.failureReason());
    }
  }

  @Post("/run")
  public RunResponse run(RunRequest request) {
    if (request.nodes() == null || request.nodes().isEmpty()) {
      throw HttpException.badRequest("A workflow needs at least one node");
    }
    if (request.rootNodeId() == null || request.rootNodeId().isBlank()) {
      throw HttpException.badRequest("rootNodeId is required");
    }

    Map<String, Object> initialVariables =
        request.initialVariables() == null ? Map.of() : request.initialVariables();
    ExecutionLimits limits =
        new ExecutionLimits(
            request.maxSteps() == null ? ExecutionLimits.DEFAULT.maxSteps() : request.maxSteps(),
            request.maxTimeMillis() == null
                ? ExecutionLimits.DEFAULT.maxTimeMillis()
                : request.maxTimeMillis());

    try {
      Graph graph = toGraph(request);
      return RunResponse.from(GraphScheduler.run(graph, initialVariables, limits));
    } catch (RuntimeException e) {
      // Every bad-input path below throws IllegalArgumentException uniformly, plus whatever
      // a node's config coming straight from the request body's Map<String,Object> with no
      // schema validation ahead of it can throw on its own -- e.g. ClassCastException where
      // TASK's failTimes expects a number and gets a string. Caught broadly here so malformed
      // input is a 400, not an opaque 500 (review checklist P3), the same way PipelineEndpoint
      // (haystack-akka) narrows the same class of failure to a single catch at the call site.
      throw HttpException.badRequest("Invalid workflow definition: " + e.getMessage());
    }
  }

  private static Graph toGraph(RunRequest request) {
    List<GraphNode> nodes = new ArrayList<>();
    for (NodeRequest nr : request.nodes()) {
      BuiltinNodeType type;
      try {
        type = BuiltinNodeType.valueOf(nr.type().toUpperCase());
      } catch (IllegalArgumentException | NullPointerException e) {
        throw new IllegalArgumentException("Unknown node type '" + nr.type() + "'");
      }
      Map<String, Object> config = nr.config() == null ? Map.of() : nr.config();
      GraphNode node = new GraphNode(nr.id(), type.kind(), type.behavior(config), RetryConfig.DISABLED, ErrorStrategy.ABORT, Map.of());
      if (nr.retry() != null) {
        RetryRequest r = nr.retry();
        node =
            node.withRetry(
                new RetryConfig(
                    r.maxRetries() == null ? 0 : r.maxRetries(),
                    r.retryIntervalMillis() == null ? 0 : r.retryIntervalMillis(),
                    r.retryEnabled() != null && r.retryEnabled()));
      }
      if (nr.errorStrategy() != null) {
        try {
          node = node.withErrorStrategy(ErrorStrategy.valueOf(nr.errorStrategy().toUpperCase()));
        } catch (IllegalArgumentException e) {
          throw new IllegalArgumentException("Unknown errorStrategy '" + nr.errorStrategy() + "'");
        }
      }
      if (nr.defaultValues() != null) {
        node = node.withDefaultValues(nr.defaultValues());
      }
      nodes.add(node);
    }

    List<Edge> edges = new ArrayList<>();
    if (request.edges() != null) {
      int i = 0;
      for (EdgeRequest er : request.edges()) {
        edges.add(new Edge("edge_" + i++, er.tail(), er.head(), er.sourceHandle()));
      }
    }

    return new Graph(nodes, edges, request.rootNodeId());
  }

  private static List<String> describe(List<GraphEvent> events) {
    List<String> trace = new ArrayList<>();
    for (GraphEvent event : events) {
      trace.add(
          switch (event) {
            case GraphEvent.EdgeTaken e ->
                "EDGE_TAKEN " + e.sourceNodeId() + " -[" + e.sourceHandle() + "]-> " + e.targetNodeId();
            case GraphEvent.EdgeSkipped e ->
                "EDGE_SKIPPED " + e.sourceNodeId() + " -[" + e.sourceHandle() + "]-> " + e.targetNodeId();
            case GraphEvent.NodeStarted e -> "NODE_STARTED " + e.nodeId();
            case GraphEvent.NodeSucceeded e -> "NODE_SUCCEEDED " + e.nodeId();
            case GraphEvent.NodeFailed e -> "NODE_FAILED " + e.nodeId() + ": " + e.error();
            case GraphEvent.NodeRetried e -> "NODE_RETRIED " + e.nodeId() + " retryIndex=" + e.retryIndex();
          });
    }
    return trace;
  }
}
