package io.akka.dify.domain;

import java.util.Map;
import java.util.Objects;

/**
 * A node's static configuration: what runs it ({@link #behavior()}), and what happens if it
 * fails ({@link #retryConfig()}, {@link #errorStrategy()}, {@link #defaultValues()}).
 * {@link #kind()} is informational only — whether a completion is treated as a branch
 * completion (SPEC-001 R4) is decided by whether the node's {@link NodeOutcome.Success}
 * carries a {@code selectedHandle}, the same way the source promotes any node configured with
 * {@code FAIL_BRANCH} to branch-shaped behaviour for that one outcome regardless of its
 * declared kind ({@code graph.py:205-214}, {@code _promote_fail_branch_nodes}).
 */
public record GraphNode(
    String id,
    NodeKind kind,
    NodeBehavior behavior,
    RetryConfig retryConfig,
    ErrorStrategy errorStrategy,
    Map<String, Object> defaultValues) {

  public GraphNode {
    Objects.requireNonNull(id, "id");
    if (id.isBlank()) {
      throw new IllegalArgumentException("id must be non-blank");
    }
    Objects.requireNonNull(kind, "kind");
    Objects.requireNonNull(behavior, "behavior");
    retryConfig = retryConfig == null ? RetryConfig.DISABLED : retryConfig;
    errorStrategy = errorStrategy == null ? ErrorStrategy.ABORT : errorStrategy;
    defaultValues = defaultValues == null ? Map.of() : Map.copyOf(defaultValues);
  }

  public static GraphNode task(String id, NodeBehavior behavior) {
    return new GraphNode(id, NodeKind.TASK, behavior, RetryConfig.DISABLED, ErrorStrategy.ABORT, Map.of());
  }

  public static GraphNode branch(String id, NodeBehavior behavior) {
    return new GraphNode(id, NodeKind.BRANCH, behavior, RetryConfig.DISABLED, ErrorStrategy.ABORT, Map.of());
  }

  public GraphNode withRetry(RetryConfig retryConfig) {
    return new GraphNode(id, kind, behavior, retryConfig, errorStrategy, defaultValues);
  }

  public GraphNode withErrorStrategy(ErrorStrategy errorStrategy) {
    return new GraphNode(id, kind, behavior, retryConfig, errorStrategy, defaultValues);
  }

  public GraphNode withDefaultValues(Map<String, Object> defaultValues) {
    return new GraphNode(id, kind, behavior, retryConfig, errorStrategy, defaultValues);
  }
}
