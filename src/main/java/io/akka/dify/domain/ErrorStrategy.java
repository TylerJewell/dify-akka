package io.akka.dify.domain;

/**
 * What happens once a node's failure has no retry left (SPEC-001 R9). Named and valued after
 * {@code graphon.enums.ErrorStrategy}, plus the implicit {@code ABORT} the source falls back to
 * when a node has no {@code error_strategy} configured at all ({@code error_handler.py:87-95}).
 */
public enum ErrorStrategy {
  /** The run ends FAILED immediately; no further nodes run. This is the default. */
  ABORT,
  /** The failure is treated as success, routed through the {@code "fail-branch"} handle. */
  FAIL_BRANCH,
  /** The failure is treated as success, routed through the node's plain outgoing edges. */
  DEFAULT_VALUE
}
