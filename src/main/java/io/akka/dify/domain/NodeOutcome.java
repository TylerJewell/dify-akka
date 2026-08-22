package io.akka.dify.domain;

import java.util.Map;

/**
 * What a single execution attempt of a node produced. Mirrors the two terminal statuses
 * {@code graphon}'s {@code NodeRunResult} converts a node's return value into
 * (SUCCEEDED / FAILED — {@code nodes/base/node.py:819-856}); the other statuses the source
 * enumerates (PENDING, RUNNING, EXCEPTION, STOPPED, PAUSED, RETRY) belong to node kinds or
 * lifecycle states this slice does not port (SPEC-001 §1).
 */
public sealed interface NodeOutcome {

  record Success(Map<String, Object> outputs, String selectedHandle) implements NodeOutcome {
    public Success {
      outputs = outputs == null ? Map.of() : Map.copyOf(outputs);
    }
  }

  record Failure(String errorMessage) implements NodeOutcome {
    public Failure {
      if (errorMessage == null || errorMessage.isBlank()) {
        throw new IllegalArgumentException("errorMessage must be non-blank");
      }
    }
  }

  /** A non-branch success: every outgoing edge is taken (SPEC-001 R3). */
  static NodeOutcome success(Map<String, Object> outputs) {
    return new Success(outputs, null);
  }

  /** A branch success: only the edge(s) matching {@code selectedHandle} are taken (R4). */
  static NodeOutcome success(Map<String, Object> outputs, String selectedHandle) {
    return new Success(outputs, selectedHandle);
  }

  static NodeOutcome failure(String errorMessage) {
    return new Failure(errorMessage);
  }
}
