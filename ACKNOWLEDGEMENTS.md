# Acknowledgements

This project is a port of **[langgenius/dify](https://github.com/langgenius/dify)** — more
precisely, of `graphon`, the standalone package (published separately at
[langgenius/graphon](https://github.com/langgenius/graphon), pinned as `graphon==0.7.0` in
`langgenius/dify`'s own `api/pyproject.toml:48`) that actually implements the workflow
execution engine this port's slice covers. See `question-log.md` row 1 in the port record
for how that was established.

## Licence and copyright

- **`langgenius/dify` itself** is licensed under a modified Apache License 2.0 with
  additional conditions (no unauthorized multi-tenant hosting, no removing the console's
  LOGO/copyright notice) — see its own `LICENSE` file. This port never read or ran any code
  from that repository directly.
- **`graphon`**, the package this port actually read and ran, ships under a plain,
  unmodified **Apache License 2.0**, Copyright 2026 LangGenius, Inc. (`LICENSE-dify:189`,
  and `pip show graphon` reports `License-Expression: Apache-2.0` with no additional
  conditions). This is a materially simpler licensing position than `langgenius/dify`'s own
  modified terms, and it is the one that actually governs the material this port is derived
  from.
- **Nothing was copied verbatim.** Every Java file under `dify-akka/src` was written fresh
  against behaviour read out of, and run against, the installed `graphon` 0.7.0 Python
  package; no source text, comments, or test fixtures were transcribed. Where a comment or
  the spec cites a source file and line range, that is citation, not copying.
- **Behaviour is derived throughout**, plainly: the three-state (UNKNOWN / TAKEN / SKIPPED)
  edge model, the OR-join readiness rule and recursive skip propagation, and the per-node
  retry-then-error-strategy fallthrough (abort / fail-branch / default-value) are a direct
  port of the decision procedure in `graphon`'s `graph_engine/graph_state_manager.py`,
  `graph_engine/graph_traversal/edge_processor.py`,
  `graph_engine/graph_traversal/skip_propagator.py`, and `graph_engine/error_handler.py`.
  This is the nature of a port and is not something to obscure.
- Because no Apache-2.0 text was copied into this repository, nothing here is bound by
  `graphon`'s licence terms beyond attribution — the "copied material carries its licence
  with it" rule does not trigger, since nothing was copied. `LICENSE-dify` carries
  `graphon`'s original licence text for reference and attribution only.

## Also used

- [Akka](https://akka.io) — the SDK and runtime this port is built on.
