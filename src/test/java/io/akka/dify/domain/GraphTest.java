package io.akka.dify.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/** SPEC-001 §4 OD4: the port's narrower, scheduler-scoped graph validation. */
class GraphTest {

  private static GraphNode plain(String id) {
    return GraphNode.task(id, v -> NodeOutcome.success(Map.of()));
  }

  @Test
  void rejectsMissingRoot() {
    assertThatThrownBy(
            () -> new Graph(List.of(plain("a")), List.of(), "missing"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("missing");
  }

  @Test
  void rejectsEdgeToUnknownNode() {
    assertThatThrownBy(
            () ->
                new Graph(
                    List.of(plain("a")),
                    List.of(new Edge("e0", "a", "ghost")),
                    "a"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("ghost");
  }

  @Test
  void rejectsUnreachableNode() {
    assertThatThrownBy(
            () ->
                new Graph(
                    List.of(plain("a"), plain("orphan")),
                    List.of(),
                    "a"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("orphan");
  }

  @Test
  void acceptsAConnectedGraph() {
    Graph graph =
        new Graph(
            List.of(plain("a"), plain("b")),
            List.of(new Edge("e0", "a", "b")),
            "a");

    assertThat(graph.rootNodeId()).isEqualTo("a");
    assertThat(graph.outgoing("a")).hasSize(1);
    assertThat(graph.incoming("b")).hasSize(1);
  }
}
