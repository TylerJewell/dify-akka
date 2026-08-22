package io.akka.dify.domain;

import java.util.Objects;

/**
 * A directed edge between two nodes. {@code sourceHandle} distinguishes a branch node's
 * outgoing edges ({@code "true"} / {@code "false"} / {@code "fail-branch"} / ...); a plain
 * edge uses {@code "source"}, matching {@code graphon}'s default
 * ({@code graph/graph.py:131}).
 */
public record Edge(String id, String tail, String head, String sourceHandle) {

  public static final String DEFAULT_HANDLE = "source";

  public Edge {
    Objects.requireNonNull(id, "id");
    Objects.requireNonNull(tail, "tail");
    Objects.requireNonNull(head, "head");
    sourceHandle = sourceHandle == null || sourceHandle.isBlank() ? DEFAULT_HANDLE : sourceHandle;
  }

  public Edge(String id, String tail, String head) {
    this(id, tail, head, DEFAULT_HANDLE);
  }
}
