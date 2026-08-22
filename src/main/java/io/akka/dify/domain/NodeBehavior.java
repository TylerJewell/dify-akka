package io.akka.dify.domain;

import java.util.Map;

/**
 * A node's body — the thing this port explicitly does not implement for dify's own node
 * catalog (SPEC-001 §1). Every {@link GraphNode} is driven by one of these, given the run's
 * accumulated variables (seeded with the caller's initial input, then extended with
 * {@code "<nodeId>.<outputKey>"} after every node completes, the same addressing
 * {@code graphon}'s {@code VariablePool} uses).
 */
@FunctionalInterface
public interface NodeBehavior {
  NodeOutcome run(Map<String, Object> variables);
}
