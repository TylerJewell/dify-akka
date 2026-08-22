package io.akka.dify.api;

import static org.assertj.core.api.Assertions.assertThat;

import akka.javasdk.testkit.TestKitSupport;
import io.akka.dify.api.WorkflowRunEndpoint.EdgeRequest;
import io.akka.dify.api.WorkflowRunEndpoint.NodeRequest;
import io.akka.dify.api.WorkflowRunEndpoint.RetryRequest;
import io.akka.dify.api.WorkflowRunEndpoint.RunRequest;
import io.akka.dify.api.WorkflowRunEndpoint.RunResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

/**
 * The capability driven the way a caller outside a test would drive it: over HTTP, against a
 * started runtime. {@code GraphSchedulerTest} checks SPEC-001's rules directly; this checks
 * that something outside a test can reach them at all (PIPELINE.md step d's reachability
 * question).
 */
public class WorkflowRunEndpointTest extends TestKitSupport {

  private RunResponse run(RunRequest request) {
    return httpClient.POST("/workflows/run").withRequestBody(request).responseBodyAs(RunResponse.class).invoke().body();
  }

  @Test
  public void branchSkipsTheUnselectedPathOverHttp() {
    var request =
        new RunRequest(
            List.of(
                new NodeRequest("start", "task", Map.of("outputs", Map.of("approved", true)), null, null, null),
                new NodeRequest(
                    "branch", "if_else", Map.of("conditionVariable", "start.approved"), null, null, null),
                new NodeRequest("approved", "task", Map.of("outputs", Map.of("ok", true)), null, null, null),
                new NodeRequest("rejected", "task", Map.of("outputs", Map.of("ok", false)), null, null, null)),
            List.of(
                new EdgeRequest("start", "branch", null),
                new EdgeRequest("branch", "approved", "true"),
                new EdgeRequest("branch", "rejected", "false")),
            "start",
            Map.of(),
            null,
            null);

    RunResponse response = run(request);

    assertThat(response.outcome()).isEqualTo("SUCCEEDED");
    assertThat(response.outputsByNode()).doesNotContainKey("rejected");
    assertThat(response.trace()).contains("NODE_SUCCEEDED approved");
  }

  @Test
  public void aRetryingTaskEventuallySucceedsOverHttp() {
    var request =
        new RunRequest(
            List.of(
                new NodeRequest(
                    "flaky",
                    "task",
                    Map.of("mode", "flaky", "failTimes", 2, "errorMessage", "boom"),
                    new RetryRequest(3, 1L, true),
                    null,
                    null)),
            List.of(),
            "flaky",
            Map.of(),
            null,
            null);

    RunResponse response = run(request);

    assertThat(response.outcome()).isEqualTo("PARTIAL_SUCCEEDED");
    assertThat(response.exceptionsCount()).isEqualTo(2);
    assertThat(response.trace()).contains("NODE_RETRIED flaky retryIndex=1", "NODE_RETRIED flaky retryIndex=2");
  }

  @Test
  public void anUnreachableNodeReturnsBadRequestNotAServerError() {
    var request =
        new RunRequest(
            List.of(
                new NodeRequest("a", "task", Map.of(), null, null, null),
                new NodeRequest("orphan", "task", Map.of(), null, null, null)),
            List.of(),
            "a",
            Map.of(),
            null,
            null);

    var response = httpClient.POST("/workflows/run").withRequestBody(request).invoke();

    assertThat(response.httpResponse().status().intValue()).isEqualTo(400);
  }
}
