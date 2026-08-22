package io.akka.dify.domain;

import java.util.Map;

/**
 * A small, fixed vocabulary of node behaviours (SPEC-001 §1 OD3), so a run can be described and
 * driven entirely as data over HTTP without accepting arbitrary code — the same reason
 * {@code haystack-akka}'s {@code BuiltinComponentType} exists. {@code TASK} stands in for every
 * dify node kind that shares the "can retry, can error-strategy-fallback" shape (code, HTTP,
 * LLM, tool); {@code IF_ELSE} exercises branching.
 */
public enum BuiltinNodeType {
  TASK {
    @Override
    public NodeKind kind() {
      return NodeKind.TASK;
    }

    @Override
    public NodeBehavior behavior(Map<String, Object> config) {
      String mode = (String) config.getOrDefault("mode", "succeed");
      String errorMessage = (String) config.getOrDefault("errorMessage", "task failed");
      return switch (mode) {
        case "succeed" -> Behaviors.succeed(outputsOf(config));
        case "fail" -> Behaviors.alwaysFail(errorMessage);
        case "flaky" -> Behaviors.flaky(intOf(config, "failTimes", 0), errorMessage);
        default -> throw new IllegalArgumentException("Unknown task mode: " + mode);
      };
    }
  },

  IF_ELSE {
    @Override
    public NodeKind kind() {
      return NodeKind.BRANCH;
    }

    @Override
    public NodeBehavior behavior(Map<String, Object> config) {
      Object conditionVariable = config.get("conditionVariable");
      if (!(conditionVariable instanceof String selector) || selector.isBlank()) {
        throw new IllegalArgumentException("if-else node requires a non-blank 'conditionVariable'");
      }
      return new IfElseBehavior(selector);
    }
  };

  public abstract NodeKind kind();

  public abstract NodeBehavior behavior(Map<String, Object> config);

  @SuppressWarnings("unchecked")
  private static Map<String, Object> outputsOf(Map<String, Object> config) {
    Object outputs = config.get("outputs");
    return outputs == null ? Map.of() : (Map<String, Object>) outputs;
  }

  private static int intOf(Map<String, Object> config, String key, int defaultValue) {
    Object value = config.get(key);
    return value == null ? defaultValue : ((Number) value).intValue();
  }
}
