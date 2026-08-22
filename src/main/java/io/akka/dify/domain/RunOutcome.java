package io.akka.dify.domain;

/**
 * How a run ended (SPEC-001 R11). Named after {@code graphon}'s
 * {@code GraphRunSucceededEvent} / {@code GraphRunPartialSucceededEvent} /
 * {@code GraphRunFailedEvent} / {@code GraphRunAbortedEvent}
 * ({@code graph_events/graph.py}).
 */
public enum RunOutcome {
  /** No more ready nodes, and no attempt ever failed. */
  SUCCEEDED,
  /** No more ready nodes, but at least one attempt failed along the way (SPEC-001 R8). */
  PARTIAL_SUCCEEDED,
  /** A node failed with no retry left and {@code ErrorStrategy.ABORT} (SPEC-001 R9). */
  FAILED,
  /** An execution limit (step count or wall-clock time) fired (SPEC-001 R10). */
  ABORTED
}
