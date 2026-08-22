package io.akka.dify.domain;

/**
 * Step-count and wall-clock ceilings on a single run (SPEC-001 R10). Defaults match dify's own
 * {@code WORKFLOW_MAX_EXECUTION_STEPS} / {@code WORKFLOW_MAX_EXECUTION_TIME}
 * ({@code api/configs/feature/__init__.py:880-892}: 500 steps, 1200 seconds).
 */
public record ExecutionLimits(int maxSteps, long maxTimeMillis) {

  public static final ExecutionLimits DEFAULT = new ExecutionLimits(500, 1_200_000L);

  public ExecutionLimits {
    if (maxSteps <= 0) {
      throw new IllegalArgumentException("maxSteps must be positive");
    }
    if (maxTimeMillis <= 0) {
      throw new IllegalArgumentException("maxTimeMillis must be positive");
    }
  }
}
