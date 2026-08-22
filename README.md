# dify-akka

Decides which step of a graph runs next, which paths through it are taken or skipped, and
whether a step that fails tries again, falls back, or stops the whole run.

A port of [langgenius/dify](https://github.com/langgenius/dify) onto **Akka**, built with
**Akka Specify**.

---

## Where it came from

langgenius/dify is a platform for building AI workflows: you wire named steps together into
a graph, and its engine works out which step runs next, which paths branch one way or
another, and what happens when a step fails — including trying again on its own, falling
back to a stand-in answer, or stopping everything. It was ported to derive a specification
format precise enough to regenerate a system on a different stack — the port is the vehicle,
the specification is the deliverable.

The specifications the port was generated from are in
[TylerJewell/akka-specify-harness](https://github.com/TylerJewell/akka-specify-harness)
under `dify-port/`.

---

## langgenius/dify → this port

📉 517 Python lines → **472 Java lines**<br>
📁 6 files → **16 files**<br>
⚡ 109,859,426-332,469,500 → **1,355-3,389** nanoseconds per run, across 7 graph shapes<br>
🎯 7 of 7 → **7 of 7** graph runs giving the same answer<br>
🧪 not measured → **6 of 6** deliberate breakages caught by a check

Full method and the numbers that did *not* make this list:
[`bench/REPORT.md`](https://github.com/TylerJewell/akka-specify-harness/blob/main/dify-port/bench/REPORT.md).

---

## What it took to build

⏱️ **1.1 hours** from the first command to the published repository, **1.1** of them active<br>
💬 **533** exchanges with the model<br>
✍️ **402,521** tokens written by the model, **168,455,055** counting everything sent and re-sent<br>
🙋 **0** questions to a human<br>
🧪 **20** tests

```bash
python toolkit/tokens.py --port dify    # turns, tokens, elapsed and active time
```

The record of every question, and where the time went, is in
[`port-log/`](https://github.com/TylerJewell/akka-specify-harness/tree/main/port-log).

---

## What it does

From the specification:

- **A step becomes ready to run once every path that could still reach it has been decided,
  and at least one of them said yes.** It does not wait for every path leading in — only
  for none of them to still be undecided.
- **Choosing one path out of a step closes every other path leading away from it, and that
  closing spreads forward on its own.** Anything further along that has no other way of
  being reached goes quiet too, without that being a separate decision.
- **A step that fails can try again on its own, a fixed number of times — but only if it was
  told in advance that trying again is allowed.** A limit on how many tries a step gets
  changes nothing by itself.
- **Once a step has run out of tries, what happens next was decided ahead of time:** stop
  the whole run, carry on down a path set aside for exactly this, or carry on with a
  stand-in answer in the step's place.
- **A run that had to fall back even once is not reported the same as a run where nothing
  ever went wrong** — even when the very last attempt of every step succeeded.

---

## Design decisions

**Single-threaded dispatch.** The original spreads ready steps across a pool of worker
threads that grows and shrinks with how busy a run is. This port runs one step at a time,
in the order it became ready, because nothing about which step goes next, or what a run
finally decides, depends on whether two ready steps happen to overlap in real time.

**A recovered failure still counts.** A step that fails, tries again, and eventually
succeeds looks clean from the outside — its last attempt worked. This port still remembers
that it took more than one try, and reports the whole run as only partly successful,
because a caller deciding whether to trust the result needs to know a retry happened even
when nothing looks broken.

**A finished run always hands back its full history.** A run that stops partway through
still has a record of everything that happened before it stopped — which steps ran, which
paths were taken or skipped. This port always returns that record together with the reason
it stopped, rather than a caller having to catch an error before seeing any of it.

**A run is checked against a shape it can actually finish in.** A run given a step that
nothing can ever reach would sit there forever with no explanation. This port refuses to
start a run like that, and says exactly which step nothing leads to, before any time is
spent on it.

**A short, fixed list of steps, not an open door for arbitrary code.** A caller building a
run picks from a small set of building blocks — do something and report the result, or
choose one of two paths onward — rather than supplying code of their own, because accepting
somebody else's code to run over an open connection is a different kind of promise than
this port makes.

---

## Running it — the short path

You do not need Java, Maven, or the Akka CLI installed. Akka Specify installs them for you.

**1. Install Akka Specify** in Claude Code:

```
/plugin marketplace add akka/ai-marketplace
/plugin install akka@akka-ai-marketplace
```

Restart Claude Code when it asks.

**2. Give it this prompt:**

> Clone https://github.com/TylerJewell/dify-akka into a new directory and open it.
> Then run /akka:setup to install everything this project needs, and /akka:build to
> compile it, run the tests, and start it locally.

**3. Open** http://localhost:9054.

---

## Running it — the developer path

### Requirements

- Java 21 or newer
- Maven 3.9 or newer
- An Akka download token — run `akka code token` once

### Start the service

```bash
mvn compile
akka local run
```

The service starts on **port 9054**.

### Try it

```bash
# a step that tries again on its own after two failures, then succeeds
curl -s localhost:9054/workflows/run -H 'content-type: application/json' -d '{
  "nodes": [
    {"id": "flaky", "type": "task",
     "config": {"mode": "flaky", "failTimes": 2, "errorMessage": "transient"},
     "retry": {"maxRetries": 3, "retryIntervalMillis": 0, "retryEnabled": true}}
  ],
  "edges": [],
  "rootNodeId": "flaky"
}'

# a step that chooses one of two paths onward, and the other never runs
curl -s localhost:9054/workflows/run -H 'content-type: application/json' -d '{
  "nodes": [
    {"id": "start", "type": "task", "config": {"outputs": {"approved": true}}},
    {"id": "branch", "type": "if_else", "config": {"conditionVariable": "start.approved"}},
    {"id": "approved", "type": "task", "config": {"outputs": {"path": "approved"}}},
    {"id": "rejected", "type": "task", "config": {"outputs": {"path": "rejected"}}}
  ],
  "edges": [
    {"tail": "start", "head": "branch"},
    {"tail": "branch", "head": "approved", "sourceHandle": "true"},
    {"tail": "branch", "head": "rejected", "sourceHandle": "false"}
  ],
  "rootNodeId": "start"
}'
```

---

## Configuration

| Variable | Default | Notes |
|---|---|---|
| none | — | The service reads no environment variables. The port it listens on is set in `src/main/resources/application.conf`. |

This port calls no model provider, so it needs no key for one.

---

## Where it differs from langgenius/dify

Everything not listed here behaves the same way on purpose, including the parts that look
like mistakes.

- **How many steps run at once.** The original spreads ready steps across a pool of worker
  threads that grows and shrinks with how busy a run is. This port runs one step at a time,
  in the order it became ready, because the scheduling rules this port rebuilds — which
  step goes next, which paths are taken or skipped, whether a run partly succeeded — do not
  depend on real-time overlap between steps. **Not checked** against the original for a
  graph wide enough for the difference to show up in a timing; every graph shape this port
  measured is small.
- **What a caller sees for a step that failed and then fell back.** The original reports a
  step that used a fallback (rather than trying again, or stopping the run) with a
  different label from a step that simply succeeded, even though both let the run continue.
  This port reports both the same way — the run continues either way, the same paths are
  taken or skipped either way, and the run is still marked as only partly successful either
  way — because the slice this port rebuilds is what happens next, not what a step's own
  outcome is called.
- **What a caller gets back from a run that stops partway through.** The original hands
  back everything that happened up to the point of stopping, but only to a caller reading
  it step by step as it happens — collecting the whole thing into one list first loses
  everything that happened before the stop. This port always hands back the complete record
  together with the reason it stopped, because a caller of an HTTP endpoint has no
  equivalent of reading step by step as it happens.
- **How much a graph is checked before it runs.** The original checks a graph thoroughly
  before any of it runs — far beyond what this port's slice covers. This port checks only
  what the scheduling rules themselves depend on: that every step is reachable from the
  start, and that every path names a step that actually exists. A step nothing can ever
  reach is refused outright rather than being accepted and left to sit forever; anything
  stricter than that is not this port's concern.
- **What a step may be.** The original ships around two dozen kinds of step — calling a
  language model, making a web request, running a sandboxed script, searching a knowledge
  base, and more. This port accepts only two: doing something and reporting a plain result,
  and choosing one of two paths onward based on one yes-or-no answer. Neither the choosing
  of a next step nor what happens when a step fails is affected by which kind of step it
  is, so the two included here are enough to exercise every rule this port rebuilds.
- **How a choice between two paths is decided.** The original lets a single step compare
  several conditions at once, combined with "and"/"or", against many different kinds of
  value. This port compares exactly one already-computed yes-or-no answer. Which path gets
  taken and which gets skipped, and how that spreads forward through the rest of the graph,
  works exactly the same either way — only the language for writing the condition itself is
  smaller.

---

## Licence

`graphon`, the package this port's slice actually reads and runs against (see
`ACKNOWLEDGEMENTS.md`), is under the Apache License 2.0, © 2026 LangGenius, Inc. This port
reimplements the behaviour without copied source; see `ACKNOWLEDGEMENTS.md`.
