package io.akka.dify.domain;

import java.util.Map;

/**
 * The run's trace, in emission order. Named after {@code graphon}'s
 * {@code GraphEdgeTakenEvent} / {@code GraphEdgeSkippedEvent} / {@code NodeRun*Event} family
 * ({@code graph_events/traversal.py}, {@code graph_events/node.py}), trimmed to the events
 * SPEC-001's rules actually produce.
 */
public sealed interface GraphEvent {

  record EdgeTaken(String edgeId, String sourceNodeId, String targetNodeId, String sourceHandle)
      implements GraphEvent {}

  record EdgeSkipped(String edgeId, String sourceNodeId, String targetNodeId, String sourceHandle)
      implements GraphEvent {}

  record NodeStarted(String nodeId) implements GraphEvent {}

  record NodeSucceeded(String nodeId, Map<String, Object> outputs) implements GraphEvent {}

  record NodeFailed(String nodeId, String error) implements GraphEvent {}

  /** {@code retryIndex} ranges over exactly 1..maxRetries (SPEC-001 R7). */
  record NodeRetried(String nodeId, int retryIndex) implements GraphEvent {}
}
