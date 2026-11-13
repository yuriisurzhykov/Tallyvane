# knowledge-core

A proof of concept for the core claim of the product: a résumé built from what a person says about
their work, where every line traces back to their own words. The decision and the diagrams are in
[ADR-101](../../docs/adr/ADR-101-knowledge-core-proof-of-concept.md).

It is a build of its own, outside `backend/`. One hard-coded user, no authentication.

## Status

Delivered in three pull requests.

1. **The domain and the application** (merged): the rules, the use cases and the demo scenario,
   run against in-memory fakes. No database, no network, no model.
2. **Infrastructure** (this one): Postgres with pgvector, the Anthropic and Ollama clients, the
   prompts and seeds, the Ktor routes, and the same demo scenario against a real database with a
   scripted model.
3. The page with the graph, `docker-compose.yml`, the full README and an opt-in live test.

## Running the tests

```
cd poc/knowledge-core
./gradlew build
```

`:domain:test` holds the rules, one test per rule. `:application:test` holds the demo scenario of
the specification (`DemoScenarioSpec`) and the behaviour of the use cases around it. `:infra:test`
holds the routes (on the in-memory store), the clients (against a mock server) and the settings.

The tests that need a real database are a suite of their own, as ADR-057 has it, and `build` does
not run them:

```
./gradlew :infra:integrationTest
```

They run the demo of the specification over Postgres with pgvector, with a scripted model and
fake embeddings, and check the rows it leaves. They start `pgvector/pgvector:pg16` with
Testcontainers, which needs Docker; without Docker, point them at a Postgres that has pgvector
installed (the role must be able to create databases, and each test makes and drops one):

```
POC_TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/postgres \
POC_TEST_DATABASE_USER=postgres POC_TEST_DATABASE_PASSWORD=... ./gradlew :infra:integrationTest
```

## Layout

```
domain/       entities, value objects, ports; imports nothing but the JDK
application/  use cases, the reading of the model's replies, KnowledgeCore (the wiring)
              src/testFixtures: the in-memory store, the scripted model, the demo script
infra/        Postgres and pgvector (Exposed, Flyway), the Anthropic and Ollama clients, the prompts and
              the seeds as files, the Ktor routes, and Main (the composition root)
```

## Model comparison for the next interview contract

[`../experience-evals/`](../experience-evals/) is an independent Python evaluation bench for comparing API models on nested companies, projects, tasks, actions and corrections. It tests a new contract with one result object per action; it does not change or evaluate this PoC's existing many-to-many outcome implementation.
