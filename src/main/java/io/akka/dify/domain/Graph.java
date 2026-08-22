package io.akka.dify.domain;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * A workflow graph: nodes plus the edges between them, rooted at one entry node.
 *
 * <p>Validation is deliberately narrower than the source's {@code get_graph_validator()}
 * (SPEC-001 OD4): it rejects only what would make the scheduling rules in SPEC-001 §3
 * misbehave or hang silently — a missing root, an edge naming a node that does not exist, and
 * a node unreachable from the root (which could never satisfy the OR-join readiness rule and
 * would leave a run waiting on nothing, forever, with no diagnostic).
 */
public final class Graph {

  private final Map<String, GraphNode> nodes;
  private final Map<String, Edge> edges;
  private final Map<String, List<Edge>> outEdges;
  private final Map<String, List<Edge>> inEdges;
  private final String rootNodeId;

  public Graph(List<GraphNode> nodes, List<Edge> edges, String rootNodeId) {
    Map<String, GraphNode> nodeMap = new LinkedHashMap<>();
    for (GraphNode node : nodes) {
      if (nodeMap.putIfAbsent(node.id(), node) != null) {
        throw new IllegalArgumentException("Duplicate node id: " + node.id());
      }
    }
    if (rootNodeId == null || !nodeMap.containsKey(rootNodeId)) {
      throw new IllegalArgumentException("rootNodeId '" + rootNodeId + "' not found in the graph");
    }

    Map<String, Edge> edgeMap = new LinkedHashMap<>();
    Map<String, List<Edge>> out = new LinkedHashMap<>();
    Map<String, List<Edge>> in = new LinkedHashMap<>();
    for (Edge edge : edges) {
      if (edgeMap.putIfAbsent(edge.id(), edge) != null) {
        throw new IllegalArgumentException("Duplicate edge id: " + edge.id());
      }
      if (!nodeMap.containsKey(edge.tail())) {
        throw new IllegalArgumentException("Edge '" + edge.id() + "' tail '" + edge.tail() + "' not found");
      }
      if (!nodeMap.containsKey(edge.head())) {
        throw new IllegalArgumentException("Edge '" + edge.id() + "' head '" + edge.head() + "' not found");
      }
      out.computeIfAbsent(edge.tail(), k -> new ArrayList<>()).add(edge);
      in.computeIfAbsent(edge.head(), k -> new ArrayList<>()).add(edge);
    }

    this.nodes = Map.copyOf(nodeMap);
    this.edges = Map.copyOf(edgeMap);
    this.outEdges = out;
    this.inEdges = in;
    this.rootNodeId = rootNodeId;

    checkReachability(nodeMap.keySet());
  }

  private void checkReachability(Set<String> allNodeIds) {
    Set<String> reachable = new LinkedHashSet<>();
    Deque<String> frontier = new ArrayDeque<>();
    frontier.add(rootNodeId);
    reachable.add(rootNodeId);
    while (!frontier.isEmpty()) {
      String current = frontier.poll();
      for (Edge edge : outEdges.getOrDefault(current, List.of())) {
        if (reachable.add(edge.head())) {
          frontier.add(edge.head());
        }
      }
    }
    for (String nodeId : allNodeIds) {
      if (!reachable.contains(nodeId)) {
        throw new IllegalArgumentException("Node '" + nodeId + "' is unreachable from root '" + rootNodeId + "'");
      }
    }
  }

  public String rootNodeId() {
    return rootNodeId;
  }

  public GraphNode node(String nodeId) {
    GraphNode node = nodes.get(nodeId);
    if (node == null) {
      throw new IllegalArgumentException("Unknown node id: " + nodeId);
    }
    return node;
  }

  public List<Edge> outgoing(String nodeId) {
    return outEdges.getOrDefault(nodeId, List.of());
  }

  public List<Edge> incoming(String nodeId) {
    return inEdges.getOrDefault(nodeId, List.of());
  }

  public Map<String, Edge> edges() {
    return edges;
  }

  public Map<String, GraphNode> nodes() {
    return nodes;
  }
}
