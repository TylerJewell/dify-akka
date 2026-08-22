package io.akka.dify.bench;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.akka.dify.domain.BuiltinNodeType;
import io.akka.dify.domain.Edge;
import io.akka.dify.domain.ErrorStrategy;
import io.akka.dify.domain.ExecutionLimits;
import io.akka.dify.domain.Graph;
import io.akka.dify.domain.GraphNode;
import io.akka.dify.domain.GraphScheduler;
import io.akka.dify.domain.RetryConfig;
import io.akka.dify.domain.RunResult;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * The port's side of the benchmark: this rebuild answering every workload in
 * {@code dify-port/bench/workloads.json}, in the same normalized shape
 * {@code dify-port/bench/source_run.py} writes for the source side.
 *
 * <pre>
 *   mvn -q exec:java -Dexec.mainClass=io.akka.dify.bench.BenchRunner -Dexec.args="workloads.json answers"
 * </pre>
 *
 * or, once compiled, directly on the classpath as any other {@code main}.
 */
public final class BenchRunner {

  private static final ObjectMapper JSON = new ObjectMapper();
  private static final int WARMUP_REPETITIONS = 20_000;
  private static final int TIMING_REPETITIONS = 20_000;

  private BenchRunner() {}

  public static void main(String[] args) throws IOException {
    var workloads = (ArrayNode) JSON.readTree(Files.readString(Path.of(args[0])));
    var mode = args.length > 1 ? args[1] : "answers";
    if (mode.equals("timings")) {
      System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(timings(workloads)));
    } else {
      System.out.println(JSON.writerWithDefaultPrettyPrinter().writeValueAsString(answers(workloads)));
    }
  }

  private static ObjectNode answers(ArrayNode workloads) {
    var out = JSON.createObjectNode();
    for (var workload : workloads) {
      var name = workload.get("name").asText();
      RunResult result = GraphScheduler.run(build(workload), Map.of(), ExecutionLimits.DEFAULT);
      out.set(name, toJson(result));
    }
    return out;
  }

  private static ObjectNode timings(ArrayNode workloads) {
    var out = JSON.createObjectNode();
    for (var workload : workloads) {
      var name = workload.get("name").asText();
      // Rebuilds the graph inside the loop, matching source_timings.py: graphon's Node/Edge
      // objects carry mutable per-run state directly, so its own public API cannot reuse one
      // Graph across runs, and a real caller (dify's own app runner) rebuilds one every time
      // it starts a workflow. This port's Graph/GraphNode/Edge are immutable and safely
      // reusable (all per-run state lives in RunState instead) -- a real difference, not
      // something to hide by building once here and paying construction cost only on the
      // other side. See bench/REPORT.md.
      for (int i = 0; i < WARMUP_REPETITIONS; i++) {
        GraphScheduler.run(build(workload), Map.of(), ExecutionLimits.DEFAULT);
      }

      long start = System.nanoTime();
      for (int i = 0; i < TIMING_REPETITIONS; i++) {
        GraphScheduler.run(build(workload), Map.of(), ExecutionLimits.DEFAULT);
      }
      long elapsedNs = System.nanoTime() - start;

      var row = JSON.createObjectNode();
      row.put("repetitions", TIMING_REPETITIONS);
      row.put("nsPerOp", elapsedNs / (double) TIMING_REPETITIONS);
      out.set(name, row);
    }
    return out;
  }

  private static ObjectNode toJson(RunResult result) {
    var out = JSON.createObjectNode();
    out.put("outcome", result.outcome().name());
    var completed = out.putArray("completedNodes");
    // Both a clean success and a FAIL_BRANCH/DEFAULT_VALUE recovery land here -- see
    // source_run.py's matching definition of "completedNodes" and bench/REPORT.md.
    new TreeMap<>(result.outputsByNode()).keySet().forEach(completed::add);
    out.put("exceptionsCount", result.exceptionsCount());
    return out;
  }

  private static Graph build(JsonNode workload) {
    List<GraphNode> nodes = new ArrayList<>();
    for (JsonNode nr : workload.get("nodes")) {
      BuiltinNodeType type = BuiltinNodeType.valueOf(nr.get("type").asText().toUpperCase());
      Map<String, Object> config = toMap(nr.get("config"));
      GraphNode node =
          new GraphNode(
              nr.get("id").asText(), type.kind(), type.behavior(config), RetryConfig.DISABLED, ErrorStrategy.ABORT, Map.of());
      if (nr.has("retry")) {
        JsonNode r = nr.get("retry");
        node =
            node.withRetry(
                new RetryConfig(
                    r.get("maxRetries").asInt(), r.get("retryIntervalMillis").asLong(), r.get("retryEnabled").asBoolean()));
      }
      if (nr.has("errorStrategy")) {
        node = node.withErrorStrategy(ErrorStrategy.valueOf(nr.get("errorStrategy").asText().toUpperCase()));
      }
      if (nr.has("defaultValues")) {
        node = node.withDefaultValues(toMap(nr.get("defaultValues")));
      }
      nodes.add(node);
    }

    List<Edge> edges = new ArrayList<>();
    int i = 0;
    for (JsonNode er : workload.get("edges")) {
      String handle = er.has("sourceHandle") ? er.get("sourceHandle").asText() : Edge.DEFAULT_HANDLE;
      edges.add(new Edge("edge_" + i++, er.get("tail").asText(), er.get("head").asText(), handle));
    }

    return new Graph(nodes, edges, workload.get("rootNodeId").asText());
  }

  private static Map<String, Object> toMap(JsonNode node) {
    Map<String, Object> map = new java.util.LinkedHashMap<>();
    if (node == null || node.isNull()) {
      return map;
    }
    Iterator<Map.Entry<String, JsonNode>> fields = node.properties().iterator();
    while (fields.hasNext()) {
      var entry = fields.next();
      map.put(entry.getKey(), JSON.convertValue(entry.getValue(), Object.class));
    }
    return map;
  }
}
