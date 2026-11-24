# Interactive experience lab

**Goal:** Let a person type work-history messages, select different API models, and see state changes immediately in a browser.

**Design:** Extend the existing Python bench with a loopback-only HTTP server and a buildless Russian web interface. Reuse the prompt, adapters, strict contract and atomic reducer. Keep the domain graph distinct from embeddings: this lab does not calculate embeddings or pretend graph edges are vector similarity. A guided offline scenario is explicitly scripted; arbitrary text uses a live configured API. Sessions stay in server memory and can be exported as JSON. A fork copies the exact state for comparison with another model.

**Approaches:** A separate hosted app would duplicate the Python reducer; a terminal-only UI would not satisfy the request. Add a web interface to the current repository and PR. This is the implementation choice made from the user's correction, not an additional requirement from them.

**Tech stack:** Python standard-library HTTP, existing Pydantic/PyYAML, HTML/CSS/JavaScript/SVG, no external browser assets.

**Constraints:** API keys remain in environment variables or transient request memory; no keys in session exports. Bind only to loopback; check Host, Origin and a per-process request token. Preserve failed input and diagnostics while applying no partial domain updates. Serialise changes to one session. Do not merge or change master.

## Tasks

- [x] Write failing service tests: offline create/correct, arbitrary offline rejection, fork isolation, failed live atomicity, redelivery, invalid context, secret redaction.
- [x] Implement `web.py` service and HTTP endpoints; add `serve` CLI and optional per-request API key support.
- [x] Build interactive workspace: provider/model controls, guided examples and free input, selectable graph, entity details, per-turn request/response/diff/state diagnostics, fork and export.
- [x] Run HTTP integration tests (origin/token/body errors), all existing bench tests, demo validation, JS syntax and browser QA where available.
- [x] Document launch instructions, perform independent review, fix material issues, update draft PR.

## Review focus

Concurrent submissions must not overwrite state. A model failure must retain a message without committing changes. An offline step must never imply arbitrary text was processed by an LLM. Keys must not leak via errors, exports or bootstrap. Selecting a node must provide valid ancestor context. Large graphs must remain scrollable and inspectable.

## Verification and review

Independent reviewer reproduced a lost-response retry bug. Fixed by retaining the message ID across retries/reloads, with a failing-then-passing JavaScript regression test. A further regression proves deduplication runs before context validation after an action moves. Numeric credentials cannot corrupt numeric JSON fields; auth filtering is restricted to provider diagnostics and never rewrites canonical data. The minor live-zero-cost demo label was corrected. HTTP tests cover static delivery, request token, cross-origin rejection, malformed JSON type, concurrent turns, independent forks and atomic failures. Managed browser QA is unavailable: the preview runtime has no Python executable. Real paid API calls were not run.
