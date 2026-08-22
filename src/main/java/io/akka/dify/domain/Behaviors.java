package io.akka.dify.domain;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/** Small, deterministic {@link NodeBehavior} factories shared by tests and the bench runner. */
public final class Behaviors {

  private Behaviors() {}

  public static NodeBehavior succeed(Map<String, Object> outputs) {
    return variables -> NodeOutcome.success(outputs);
  }

  public static NodeBehavior alwaysFail(String errorMessage) {
    return variables -> NodeOutcome.failure(errorMessage);
  }

  /** Fails on its first {@code failTimes} calls, then succeeds with {@code attempt} recorded. */
  public static NodeBehavior flaky(int failTimes, String errorMessage) {
    AtomicInteger attempts = new AtomicInteger(0);
    return variables -> {
      int attempt = attempts.incrementAndGet();
      if (attempt <= failTimes) {
        return NodeOutcome.failure(errorMessage + " (attempt " + attempt + ")");
      }
      return NodeOutcome.success(Map.of("attempt", attempt));
    };
  }
}
