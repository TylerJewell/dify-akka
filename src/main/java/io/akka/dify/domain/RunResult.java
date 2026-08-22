package io.akka.dify.domain;

import java.util.List;
import java.util.Map;

/**
 * The result of running a {@link Graph} to completion (or to an abort/failure).
 *
 * <p>{@code events} always holds everything emitted up to the point the run ended, including
 * a FAILED or ABORTED run — this port does not reproduce the source's surprise where a caller
 * that wraps the whole run in a single collection call loses every event yielded before the
 * generator re-raises (question-log row 7). Surfacing a terminal {@link RunOutcome} with the
 * full trace instead of throwing is a documented improvement, not a silent behavior change.
 */
public record RunResult(
    RunOutcome outcome,
    Map<String, Map<String, Object>> outputsByNode,
    List<GraphEvent> events,
    int exceptionsCount,
    String failureReason) {

  public RunResult {
    outputsByNode = Map.copyOf(outputsByNode);
    events = List.copyOf(events);
  }
}
