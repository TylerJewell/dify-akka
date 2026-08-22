package io.akka.dify.domain;

/**
 * The two node kinds this slice ports (SPEC-001 §1 OD3). Everything else in dify's node
 * catalog (LLM, HTTP, tool, code, loop, iteration, human input, ...) is a node *body*, out of
 * scope for a port of the scheduler.
 */
public enum NodeKind {
  /** A node that always takes every outgoing edge on success. */
  TASK,
  /** A node whose success selects exactly one outgoing edge handle to take. */
  BRANCH
}
