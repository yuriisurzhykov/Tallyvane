# Experience extraction evaluation bench

Compare API models on a work-history interview that grows and changes over time. Each company has projects, each project has tasks, each task has actions, and each action has many technologies but exactly **one result object**. Missing results remain explicitly unknown.

This is a standalone Python bench, outside the Kotlin backend and its Gradle build. It evaluates the new contract discussed for Tallyvane, not the existing `poc/knowledge-core` implementation: that PoC has no task level and supports many-to-many action/outcome links. No production data or personal work-history document is included.

For the interactive browser interface, see [Interactive web lab](#interactive-web-lab). Run `python -m experience_evals serve` and open http://127.0.0.1:8765.

## Start without an API key

Requires Python 3.11 or newer. From the repository root:

```bash
cd poc/experience-evals
python -m venv .venv
```

Activate with `source .venv/bin/activate` on macOS/Linux or `.venv\Scripts\Activate.ps1` in PowerShell, then:

```bash
python -m pip install -r requirements.txt
python -m unittest discover -s tests
python -m experience_evals validate
python -m experience_evals demo --out results/demo
```

Open `results/demo/report.html` in a browser. The **OFFLINE ORACLE** uses scripted gold answers to check the reducer, scenario replay and report. Its scores are not evidence of any model's quality. No API request is made.

## Run a live comparison

Copy `providers.example.yaml` to `providers.local.yaml`. Select only the models you want to call and set their `api_key` fields directly in that file. Both the CLI and web lab use this file by default. Environment variables and browser-submitted keys are not used. Set `api_key: null` for a local API that does not require authentication. The local file is git-ignored; credentials are excluded from reports and browser responses.

Start with one scenario and one repetition:

```bash
python -m experience_evals run --config providers.local.yaml --provider groq-oss20 --scenario correction_then_addition --mode both --repeats 1 --out results/groq-smoke
```

Compare several providers against the same dataset and prompt:

```bash
python -m experience_evals run --config providers.local.yaml --provider groq-oss20 --provider openai-luna --provider gemini-lite --mode both --repeats 3 --out results/comparison
```

`run` makes real, billable API calls. Requests run sequentially, without automatic retries or silent schema-mode fallbacks. `max_output_tokens` and `timeout_seconds` are configurable. A complete run has up to `providers × modes × repetitions × 35` calls for the supplied dataset; duplicate deliveries are handled locally. Each turn is checkpointed to `turns.jsonl`, so interrupting a run preserves completed diagnostics. Exit codes: 0 means all selected checks passed, 1 means evaluated failures, 2 means invalid configuration/data, 130 means interrupted.

Provided protocol adapters:

| Protocol | Examples | Output handling |
| --- | --- | --- |
| `chat_completions` | Groq, DeepSeek, OpenAI-compatible/local servers | Explicit `json_schema`, `json`, or prompt mode |
| `responses` | OpenAI | Native JSON Schema; incomplete/refused answers fail |
| `gemini` | Google generateContent | Native `responseJsonSchema`; thinking tokens included in usage |
| `anthropic` | Anthropic Messages | Prompt JSON mode; schema validated locally |

Provider support varies by model. An HTTP 400 or malformed response is a visible failure, not permission to downgrade the requested mode. Configure protocol-specific options under `parameters`; reserved prompt, model, schema, tools and output-limit fields cannot be overridden. Local HTTP is supported on loopback only; remote endpoints require HTTPS. API redirects are disabled to avoid forwarding credentials.

The example Anthropic/local model names are placeholders: replace them with a model you can access. The Anthropic adapter does not claim native constrained decoding. Live API compatibility and model quality have not been verified by the offline suite.

## Add or edit scenarios

`scenarios/*.yaml` are executable behavior specifications. `fixtures/two-companies.yaml` provides two companies, three projects and three identically named tasks, with separately identified actions. Scenarios cover nested creation, many technologies, corrections, new actions, results and metrics, reasons, approaches, deadlines/reactions, moves, retractions, ambiguity, duplicate delivery and attempted fabrication.

The most useful starting files are:

- `correction_then_addition.yaml`: amend a seeded action while preserving other projects.
- `new_action_then_corrections.yaml`: create an action, then enrich/correct that same new server record across five turns.
- `create_hierarchy.yaml`: create multiple nested companies/projects/tasks/actions in one message.
- `ambiguous_reference.yaml`: preserve state when several actions remain plausible.

Each file has `version`, `id`, `description`, `tags`, `initial_state`, `context`, and `turns`. Each turn contains `input: {id, text}` and `expected: {changes, unresolved_references}`. Gold operations describe the intended final facts; the scorer does not require identical operation order or identical evidence-quote boundaries if the resulting state matches and all evidence checks pass.

Use `$cache` as a local creation reference within one proposal. The application allocates a permanent UUID. In a later **gold fixture**, `action_id: "@cache"` refers to that earlier gold creation. The loader resolves this testing convenience before replay. Gold reference names must be unique across creations in one scenario. Models never receive `expected`, gold operations, gold aliases or the scorer's assertions.

The output contract has 14 typed operations. Every operation has a current-message ID and an exact source quote. Descriptions, names, reasons, requirements, lessons and result descriptions are extracted verbatim; this deliberately tests factual extraction and state transitions, not paraphrase quality. Technologies and approaches are compared without regard to order or capitalization; significant punctuation in names such as C++, C# and .NET is retained. Numeric metrics require digits and a stated metric name/unit in their quote; decimal dots and commas are supported, spelled-out numbers/thousands separators are not normalized in this first version. A quote's presence is necessary evidence, not proof that the model understood it.

Each action always contains one `result` object. `not_stated` requires null description/lesson and no metrics. `achieved`, `not_achieved`, `in_progress`, and `rejected` require a stated description. A result may contain multiple metrics. Requirements and targets must not become achieved outcomes.

## What the scorer checks

- **Isolated:** each turn starts from the gold previous state. This isolates the current interpretation error.
- **Chained:** each turn starts from the model's actual previous state. This exposes cascading errors. Failed proposals leave state unchanged, while raw messages remain available.
- Every turn compares the complete resulting state and unresolved references against gold, including untouched companies/projects/tasks/actions. Existing IDs must remain stable. New UUIDs are aligned once by type, parent and verbatim name/description, and that one-to-one mapping persists through later corrections and moves. Isolated mode treats every ID in the prior gold state as stable. Ambiguous new entities with indistinguishable labels/parents cannot be aligned automatically and fail comparison; use unambiguous creation examples in this first dataset.
- The reducer checks references, evidence, digits, one-result shape, revisions, globally unique IDs and duplicate delivery, then applies the whole proposal atomically. Previous facts and corrections remain in history; retractions are soft. No production authentication or database is provided.

Run `validate` after fixture changes. It validates all gold transitions before any billable calls. A valid JSON answer can still fail because it changes the wrong project or invents an outcome. The current dataset is intentionally small and synthetic; passing it is not a production accuracy guarantee. Grow it with independently labelled, held-out cases before choosing a production model. No LLM judge is used.

## Inspect results and cost

Each output directory contains:

- `report.html`: standalone browser report with each input, response, failure and state diff.
- `results.json`: complete run data, configurations and implementation/prompt/schema/dataset fingerprints.
- `summary.csv`: whole-dialog and turn pass counts, error categories, latency, tokens and estimated cost.
- `turns.jsonl`: checkpointed diagnostics for completed turns, including interrupted runs.

Only compare live runs using the same implementation/dataset/prompt/schema fingerprints, mode and comparable settings. Prioritize complete-dialog correctness. Cost includes failed calls when the provider reports usage; missing usage or required prices yields **unknown**, never zero. A known-cost subtotal is reported separately. Reasoning tokens are counted through provider output usage; Gemini thinking tokens are added explicitly. Anthropic cache reads and writes are separate. Rates are user-editable estimates, not invoices; DeepSeek peak/off-peak rates need to match the actual request period. Local compute is not automatically free. No API winner is inferred from the offline oracle.

Combine completed runs:

```bash
python -m experience_evals report results/groq/results.json results/openai/results.json --out results/combined
```

Run output and local configuration are git-ignored. Reports contain the supplied text, full model inputs, outputs and expected states: keep private datasets and reports private.

## Contracts and reproducibility

`contracts/input.schema.json` and `contracts/output.schema.json` are generated from the same Pydantic definitions used at runtime:

```bash
python -m experience_evals schema --kind input --out contracts/input.schema.json
python -m experience_evals schema --kind output --out contracts/output.schema.json
```

The design is in `DESIGN.md`; tests cover the reducer, all gold scenarios, real HTTP request/response mappings against a local server, malformed responses, accounting and report escaping. The standalone GitHub Actions job runs the offline checks on Python 3.11 and 3.12. The main backend/frontend suites are not changed.

Protocol/pricing references checked on 2026-10-29:

- [OpenAI Structured Outputs](https://developers.openai.com/api/docs/guides/structured-outputs)
- [OpenAI GPT-6 Luna](https://developers.openai.com/api/docs/models/gpt-6-luna)
- [Groq models and rates](https://console.groq.com/docs/models)
- [Groq strict JSON Schema](https://console.groq.com/docs/structured-outputs)
- [Gemini structured outputs](https://ai.google.dev/gemini-api/docs/structured-output)
- [Gemini pricing](https://ai.google.dev/gemini-api/docs/pricing)
- [DeepSeek JSON output](https://api-docs.deepseek.com/guides/json_mode/)
- [DeepSeek rates](https://api-docs.deepseek.com/quick_start/pricing/)
## Interactive web lab

Run the interactive interface from this directory:

```bash
python -m pip install -r requirements.txt
cp providers.example.yaml providers.local.yaml
# Fill in api_key for the APIs you want to use.
python -m experience_evals serve
```

Open **http://127.0.0.1:8765** in your browser. No Node install or frontend build is required. Change the port with `--port 9000` if needed.

The interface opens a guided five-turn demo: create an action, add a technology, state a result, correct the technologies, and retract the result. The demo replays **scripted expected answers**, not an LLM. Choose any of the 27 scenarios and click **Начать новую сессию** to try another. Offline input is intentionally limited to the exact next example.

For arbitrary messages, select an API provider and put its key in `providers.local.yaml`. Each provider has an `api_key` field. Keys are loaded at server startup; restart after editing the file. The browser receives only whether a key is configured, never the credential. Keys are not included in model payloads, session exports or reports. Provider diagnostics are redacted. Ordinary text deliberately typed in the interview remains immutable user data.

To customize endpoints, model parameters, output modes or prices:

```bash
cp providers.example.yaml providers.local.yaml
# Edit providers.local.yaml and fill in api_key for each provider.
python -m experience_evals serve --config providers.local.yaml
```

The model ID can also be edited in the UI. If it differs from the configured model, cost is unknown until you add its prices in the config. Configuration defaults are examples; availability and native schema support must be checked against your account.

In the browser you can:

- Write new experience, additions and corrections in **Диалог**. One submit makes one API call; there is no automatic retry or hidden interview assistant call.
- Inspect the clickable company → project → task → action → singular result graph. Technologies, reasons, approaches, deadlines, requirements and metrics are in node details. Selecting a node sets its ancestor context for the next live message. Clear it with the context button.
- Select a processed message and inspect the logical request, system prompt, output schema, raw API response, validation errors, token usage, estimated cost, and exact field changes. **Состояние БД** shows the latest in-memory state; each step also exposes its historical state.
- Fork the current session with **Скопировать текущее состояние**, switch model and compare the same next message against the original session. Forks are independent; API failures retain the raw message and leave the graph unchanged.
- Export the complete session as JSON. Sessions survive a page reload while the server is running; pending request IDs survive transport failures to avoid duplicate paid calls. Retrying a known failed API response is a new explicit call.

This is a **local development lab**, not hosted production infrastructure. It binds only to IPv4 loopback, rejects external Host/Origin headers, and checks a per-process token on POST requests. Do not expose it through a tunnel or reverse proxy. Sessions are in server memory, with a 100-session cap; export before restarting. No database, embedding computation, vector-similarity visualization, login or import of exported sessions is implemented here. The graph represents domain relationships, not vector proximity.

Checks: `python -m unittest discover -s tests` and `node --test tests/test_pending.cjs` (Node is needed only for this frontend helper test). Browser rendering is not verified in the managed preview environment because its runtime has no Python executable.

Example credential configuration (keep the other provider fields from the template):

```yaml
providers:
  - name: openai-luna
    protocol: responses
    base_url: https://api.openai.com/v1
    model: gpt-6-luna
    api_key: "your-api-key"
    response_mode: json_schema
```
