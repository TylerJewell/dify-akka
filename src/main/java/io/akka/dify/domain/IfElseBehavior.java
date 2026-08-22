package io.akka.dify.domain;

import java.util.Map;

/**
 * A single-condition stand-in for dify's real if-else node, which evaluates a group of
 * conditions joined by an AND/OR operator against per-type comparators
 * ({@code nodes/if_else/if_else_node.py:65-94}). SPEC-001 OD3 scopes this port to one boolean
 * run variable: truthy selects the {@code "true"} handle, anything else (including a missing
 * variable) selects {@code "false"} — the same two-outcome shape the source's {@code selected_case_id}
 * ultimately reduces to for a single-condition case.
 */
public final class IfElseBehavior implements NodeBehavior {

  public static final String TRUE_HANDLE = "true";
  public static final String FALSE_HANDLE = "false";

  private final String conditionVariable;

  public IfElseBehavior(String conditionVariable) {
    if (conditionVariable == null || conditionVariable.isBlank()) {
      throw new IllegalArgumentException("conditionVariable must be non-blank");
    }
    this.conditionVariable = conditionVariable;
  }

  @Override
  public NodeOutcome run(Map<String, Object> variables) {
    boolean result = Boolean.TRUE.equals(variables.get(conditionVariable));
    String handle = result ? TRUE_HANDLE : FALSE_HANDLE;
    return NodeOutcome.success(Map.of("result", result, "selected_case_id", handle), handle);
  }
}
