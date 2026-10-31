# Experience extraction evaluation bench

Build an independently runnable evaluation bench for choosing an API model for Tallyvane's interview. The owner requested company → many projects → many tasks → many actions; each action has many technologies and exactly one result object. An unknown result is an explicit `not_stated` object, never an invented achievement.

The existing Kotlin `poc/knowledge-core` has a different contract (no tasks, many-to-many outcomes). This bench does not change or claim to test that implementation. It establishes the new extraction/update contract and its scenarios before promotion into the product.

## Approach

Use an isolated Python 3.11+ CLI, Pydantic for contracts and PyYAML for readable scenarios. A Kotlin integration would couple evaluation work to the existing contract; a hosted UI would add deployment and credential management before model quality is known. The CLI supports JSON Chat Completions-compatible APIs, OpenAI Responses, Gemini generateContent and Anthropic Messages. Provider-specific request parameters and response formats remain explicit configuration.

The input is current state, selected context, prior raw messages and one new message. Gold operations and expected state never enter live model inputs. The model returns proposed typed operations, revision and unresolved references. The application allocates IDs, checks exact evidence quotes and references, applies the entire batch atomically, preserves raw messages/history and rejects stale or repeated writes. Source evidence is necessary, not sufficient proof of interpretation.

Scenarios contain initial state, context and turns with hand-authored gold operations. Run in isolated mode (each turn starts from the gold prior state) and chained mode (each turn starts from the model's actual prior state). Compare canonical complete states, preserving existing IDs and resolving newly allocated IDs through semantic parent paths; technology ordering is immaterial. Descriptions are extracted verbatim to keep the scorer deterministic. This measures extraction fidelity, not arbitrary paraphrase quality.

Reports distinguish schema, evidence/domain, semantic and transport failures, include latency and reported token usage/cost, and retain per-attempt request/output diagnostics. A scripted oracle run is labelled offline and cannot enter a live model ranking. Missing usage/prices produce unknown cost, not zero. User-editable prices are estimates, never provider invoices. No automatic retries are hidden; repeats are independent runs. Credentials come from named environment variables and are never written to reports or source.

## Scope and limits

Support creation, technology additions/corrections, result updates, action moves, reason/approach updates and unresolved references. Cover multiple companies/projects/tasks, additions, corrections, unknown outcomes, rejected work, dates and metrics, ambiguous references, duplicate delivery and stale revisions. Authentication, persistent production storage, embeddings and automatic semantic judging are outside this bench. Full state is sent in this small version; retrieval quality requires a future independent evaluation.

## Acceptance

All synthetic gold scenarios validate and replay offline. Unit/integration checks exercise rollback, ownership through context/state, stable IDs, single result, revisions, deduplication, model request isolation, API protocol mappings, refusal/truncation/error paths and report cost arithmetic. A demo report opens without a server. Live model quality remains unverified until the owner configures API keys and runs the bench.
