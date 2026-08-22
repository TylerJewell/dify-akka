package io.akka.dify.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Mutable, single-run bookkeeping for {@link GraphScheduler}. Not shared across runs. */
final class RunState {

  private final Map<String, NodeState> nodeStates = new HashMap<>();
  private final Map<String, NodeState> edgeStates = new HashMap<>();
  private final Deque<String> readyQueue = new ArrayDeque<>();
  private final Set<String> everEnqueued = new LinkedHashSet<>();
  final Map<String, Map<String, Object>> outputsByNode = new LinkedHashMap<>();
  final List<GraphEvent> events = new ArrayList<>();
  int exceptionsCount = 0;

  NodeState nodeState(String nodeId) {
    return nodeStates.getOrDefault(nodeId, NodeState.UNKNOWN);
  }

  void setNodeState(String nodeId, NodeState state) {
    nodeStates.put(nodeId, state);
  }

  NodeState edgeState(String edgeId) {
    return edgeStates.getOrDefault(edgeId, NodeState.UNKNOWN);
  }

  void setEdgeState(String edgeId, NodeState state) {
    edgeStates.put(edgeId, state);
  }

  /**
   * Enqueue a node at most once per run. The source's {@code GraphStateManager} does not guard
   * against this directly (SPEC-001 §4 does not claim to reproduce it either way — no evidenced
   * rule exercises two incoming edges of the same non-branch node resolving to TAKEN in the same
   * dispatch step); this port adds the guard defensively so a topology that could otherwise
   * double-enqueue a join node fails safe instead of running it twice.
   */
  void enqueue(String nodeId) {
    if (everEnqueued.add(nodeId)) {
      setNodeState(nodeId, NodeState.TAKEN);
      readyQueue.addLast(nodeId);
    }
  }

  boolean hasReadyWork() {
    return !readyQueue.isEmpty();
  }

  String dequeue() {
    return readyQueue.removeFirst();
  }
}
