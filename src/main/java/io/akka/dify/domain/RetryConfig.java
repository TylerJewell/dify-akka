package io.akka.dify.domain;

/**
 * A node's retry policy. SPEC-001 R6: retry is opt-in per node — {@code retryEnabled} gates it,
 * not {@code maxRetries > 0} alone, matching {@code graphon}'s {@code Node.retry} property
 * defaulting to {@code false} for every node kind except the ones that explicitly read
 * {@code retry_config.retry_enabled} ({@code nodes/base/node.py:277},
 * {@code nodes/code/code_node.py:728-730}).
 */
public record RetryConfig(int maxRetries, long retryIntervalMillis, boolean retryEnabled) {

  public static final RetryConfig DISABLED = new RetryConfig(0, 0, false);

  public RetryConfig {
    if (maxRetries < 0) {
      throw new IllegalArgumentException("maxRetries must be non-negative");
    }
    if (retryIntervalMillis < 0) {
      throw new IllegalArgumentException("retryIntervalMillis must be non-negative");
    }
  }
}
