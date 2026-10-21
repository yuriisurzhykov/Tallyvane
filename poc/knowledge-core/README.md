# knowledge-core

A proof of concept for the core claim of the product: a résumé built from what a person says about
their work, where every line traces back to their own words. The decision and the diagrams are in
[ADR-101](../../docs/adr/ADR-101-knowledge-core-proof-of-concept.md).

It is a build of its own, outside `backend/`. One hard-coded user, no authentication.

## Status

Delivered in three pull requests.

1. **The domain and the application** (this one): the rules, the use cases and the demo scenario,
   run against in-memory fakes. No database, no network, no model.
2. Infrastructure: Postgres with pgvector, the Anthropic and Ollama clients, the Ktor routes, and
   the same demo scenario against a real database with a fake model.
3. The page with the graph, `docker-compose.yml`, the full README and an opt-in live test.

## Running the tests

```
cd poc/knowledge-core
./gradlew build
```

`:domain:test` holds the rules, one test per rule. `:application:test` holds the demo scenario of
the specification (`DemoScenarioSpec`) and the behaviour of the use cases around it.

## Layout

```
domain/       entities, value objects, ports; imports nothing but the JDK
application/  use cases, the reading of the model's replies, KnowledgeCore (the wiring)
              src/testFixtures: the in-memory store, the scripted model, the demo script
infra/        filled in by pull request 2
```
