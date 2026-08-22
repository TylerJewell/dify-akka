package io.akka.dify.domain;

/**
 * State of a node or an edge during a run. SPEC-001 §2: both nodes and edges carry this
 * three-value state, never a boolean, because "not yet decided" (UNKNOWN) has to be
 * distinguishable from "decided not to run" (SKIPPED) for the OR-join rule (SPEC-001 R2) to
 * work: a node is ready once none of its incoming edges is UNKNOWN and at least one is TAKEN.
 */
public enum NodeState {
  UNKNOWN,
  TAKEN,
  SKIPPED
}
