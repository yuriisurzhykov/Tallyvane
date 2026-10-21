# knowledge-core

A proof of concept for the core claim of the product: a résumé built from what a person says about
their work, where every line traces back to their own words. The decision and the diagrams are in
[ADR-101](../../docs/adr/ADR-101-knowledge-core-proof-of-concept.md).

You answer questions in a chat. The model turns each answer into a knowledge graph that grows on
the page, and the interview asks for what is still missing. You paste a job description, and get
the requirements you cover and the ones you do not, and a résumé in which each bullet shows the
words you said behind it. The model structures and rephrases; it never adds a fact, and the code
checks that it did not.

It is a build of its own, outside `backend/`. One hard-coded user, no authentication, no file
import or export.

## Run it

You need JDK 21, Docker, and an Anthropic API key.

```
cd poc/knowledge-core
cp .env.example .env            # then put your key in ANTHROPIC_API_KEY
docker compose up -d
./gradlew run
```

Open http://localhost:8080.

`docker compose up -d` starts Postgres with pgvector and Ollama, and a one-shot `ollama-pull` that
downloads the embedding model (about 270 MB, once). The application embeds its skill list when it
starts, so it needs that model: wait until `docker compose ps -a` shows `ollama-pull` as exited
(`docker compose logs -f ollama-pull` to watch). If the application cannot reach Ollama or
Postgres, it says which and stops.

The application is not in the compose file; `./gradlew run` runs it from this directory, where it
reads `.env`. A variable set in the shell wins over `.env`.

### Settings

| Variable | Default | |
|---|---|---|
| `ANTHROPIC_API_KEY` | | Required. |
| `LLM_MODEL` | | Required. Any model id the Anthropic Messages API accepts; `.env.example` has one. |
| `DATABASE_URL` | `jdbc:postgresql://localhost:5432/knowledge` | The database of `docker-compose.yml`. |
| `DATABASE_USER`, `DATABASE_PASSWORD` | `knowledge`, `knowledge` | |
| `OLLAMA_URL` | `http://localhost:11434` | |
| `EMBED_MODEL` | `nomic-embed-text` | The embedding model. Compose pulls this one too. |
| `EMBED_DIM` | `768` | The size of the model's vectors. It is fixed in the database the first time the application starts; start refuses a database made for another size, and a database made again (`docker compose down -v`) takes the new one. |
| `DEDUPE_THRESHOLD` | `0.85` | How alike two actions of a project must be to ask whether they are the same. |
| `TAG_THRESHOLD` | `0.55` | How near a skill must be to an action to tag it. |
| `PROMPTS_DIR` | the source tree's prompts | Where the prompts are read from; see below. |
| `ANTHROPIC_URL` | `https://api.anthropic.com` | Another host for the API, for a proxy or a stub. |
| `PORT` | `8080` | |

### The prompts

The four prompts, `extract`, `tags`, `bullets` and `vacancy`, are Markdown files in
[`infra/src/main/resources/prompts/`](infra/src/main/resources/prompts). They are read on every
call, so edit one and send the next answer: no recompiling and no restart. (When the application
runs from this directory it reads them from there; set `PROMPTS_DIR` to read them from another
directory, in which a missing file falls back to the built-in one.) Each is the cached part of the
request: the rules, the schema and examples. What changes per call is sent after it.

## The demo

Start from an empty graph (**Start over** deletes what was said and keeps the skill and technology
lists). The interview opens with *What are you working on right now?* Answer:

> С 2022 работаю в компании Acme бэкенд-инженером. База постоянно проседала под нагрузкой. Я сначала профилировал запросы и нашёл, что ~70% — это повторные чтения каталога. Предложил кеш на Redis, согласовал с DBA и внедрил. Нагрузка на базу упала примерно на 40%.

The graph shows Acme, the role Backend Engineer, a project with the problem and no title yet, four
actions (investigated, designed, communicated, built), Redis, the DBA, and the outcome of about
40%, tagged with skills such as caching, Redis and performance profiling. The new nodes are
outlined for two seconds. Select any node to see the words it came from.

The interview then asks, in this order: when you started and whether you are still there (answer
*Да, я всё ещё там работаю*), what the project was called (*Кеш каталога*), what alternatives you
considered (*Рассматривал Memcached, но Redis уже был в стеке*), and whether there is anything
else on the project. Answer *нет*; the project closes and gets its résumé bullet.

Paste this into the job description and press **Match vacancy**:

> Senior Backend Engineer. You know caching, Redis, PostgreSQL and Kafka, and work across teams. Bonus: GraphQL.

Caching and Redis come out covered, PostgreSQL and Kafka as missing, GraphQL as not in the skill
list, and the résumé shows the bullet. Hover it: the quotes under it are the sentences you typed.

If a new answer says something close to an action you already gave, the chat asks whether it is the
same; *Yes* folds it into the earlier one, *No* keeps both.

A real model does not word things the same way twice, so the graph will not match this description
to the letter; what must hold is that nothing is in it that you did not say.

### What the debug drawer shows

**Model calls** opens a drawer with every call to the model since the application started (the
last 200): which step it was, how long it took, the tokens it used and the raw request and reply.
*Input* is what was read without the cache, *cache write* is the prefix written to the cache,
*cache read* is the prefix read back from it, and *output* is what the model wrote. The first
answer writes the prefix; later ones should read it. A model caches a prefix only above a minimum
length (1024 tokens for the larger models), so the short `tags`, `bullets` and `vacancy` prompts
may show no cache reads however often they are called. A call that failed is listed too, with the
reason.

## Tests

```
./gradlew build
```

`:domain:test` holds the rules, one test per rule. `:application:test` holds the demo scenario
(`DemoScenarioSpec`) and the behaviour of the use cases around it, against an in-memory store and a
scripted model. `:infra:test` holds the routes (on the same store), the clients (against a mock
server) and the settings.

The tests that need a real database are a suite of their own, as ADR-057 has it, and `build` does
not run them:

```
./gradlew :infra:integrationTest
```

They run the demo over Postgres with pgvector, with a scripted model and fake embeddings, and check
the rows it leaves. They start `pgvector/pgvector:pg16` with Testcontainers, which needs Docker;
without Docker, point them at a Postgres that has pgvector installed (the role must be able to
create databases, and each test makes and drops one):

```
POC_TEST_DATABASE_URL=jdbc:postgresql://localhost:5432/postgres \
POC_TEST_DATABASE_USER=postgres POC_TEST_DATABASE_PASSWORD=... ./gradlew :infra:integrationTest
```

### Against the real model

One test runs the demo against the real Anthropic API and the real embeddings. It is off unless
asked for, since it costs money and needs Ollama:

```
LIVE_LLM=1 ./gradlew :infra:integrationTest --tests '*LiveDemoSpec*'
```

It reads `.env`, needs Ollama up with the embedding model and a Postgres as above, and checks what
must hold whatever the model says: the facts of the first answer are in the graph, the interview
reaches its end, the vacancy is matched, and every résumé bullet has quotes behind it and no number
the person did not say.

## What has and has not been run

The tests above pass, and the whole path, answer to résumé, has been run end to end over a real
Postgres 16 with pgvector, including through the page in a browser. The model and the embeddings
in those runs were scripted or stubbed. **Nothing has been run against the real Anthropic API or
the real Ollama**, so whether real embeddings put the right actions near the right skills at the
default thresholds, and how a real model reads the prompts, is what the live test and the debug
drawer are for.

## Layout

```
domain/       entities, value objects, ports; imports nothing but the JDK
application/  use cases, the reading of the model's replies, KnowledgeCore (the wiring)
              src/testFixtures: the in-memory store, the scripted model, the demo script
infra/        Postgres and pgvector (Exposed, Flyway), the Anthropic and Ollama clients, the prompts and
              the seeds as files, the Ktor routes, the page (src/main/resources/static), and Main
              (the composition root)
```
