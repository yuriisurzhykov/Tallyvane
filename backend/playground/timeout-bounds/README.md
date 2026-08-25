# timeout-bounds

## 2026-08-25 — what actually stops a statement blocked on a lock?

`playground/isolation/` showed that a conflicting insert waits rather than failing, and the
conclusion written from it was "client-side bounds are not substitutes for a server-side one".
That conclusion was about a repository whose pool already sets two client-side bounds —
`socketTimeout = 30` in the driver and `defaultQueryTimeout = 10` in Exposed — and
`PostgresPersistence`'s own KDoc calls them "what actually stops a hung connection". So either
the conclusion was too strong or the KDoc was wrong, and neither had been measured through the
real pool.

```bash
docker run --rm -d --name tallyvane-tb -p 5438:5432 \
  -e POSTGRES_USER=demo -e POSTGRES_PASSWORD=demo -e POSTGRES_DB=demo postgres:17-alpine

./gradlew :playground:timeout-bounds:run
```

Every bound under test is 3 s so the numbers are comparable, and the spike abandons anything
still waiting at 8 s — it cannot hang, whatever the driver does.

## What the run showed

```
A holds an uncommitted insert of 'ivan@x'. Committed rows: 0
Every bound below is 3s. The spike abandons anything still waiting at 8s.

=== pgjdbc socketTimeout, the bound PostgresPersistence relies on
  socketTimeout=3 in the JDBC url, plain DriverManager
      bound fired after 3172 ms: PSQLException sqlState=08006 An I/O error occurred while sending to the backend.
  addDataSourceProperty("socketTimeout", 3) - an Int, as the production config passes it
      STILL WAITING after 8s - no bound fired; abandoned
  addDataSourceProperty("socketTimeout", "3") - the same value as a String
      bound fired after 3013 ms: PSQLException sqlState=08006 An I/O error occurred while sending to the backend.

=== why an Int is ignored: how a value survives the trip to the driver
  Properties.put(Int) then getProperty: null
  Properties.put(String) then getProperty: 3
  both are present as entries: [alsoSocketTimeout, socketTimeout]

=== pgjdbc connectTimeout, the other Int in the production config
  connectTimeout=3 as an Int property, to an address that never answers
      STILL WAITING after 8s - no bound fired; abandoned
  connectTimeout="3" as a String property, same address
      bound fired after 4039 ms: PoolInitializationException Failed to initialize pool: The connection attempt failed.

=== Exposed defaultQueryTimeout, the other bound PostgresPersistence sets
  insert through Exposed
      bound fired after 3104 ms: ExposedSQLException sqlState=57014 ERROR: canceling statement due to user request
      pool still usable: yes
  the same insert as raw JDBC on the same pool, bypassing Exposed
      STILL WAITING after 8s - no bound fired; abandoned

=== server statement_timeout, set in the connection options
  insert through Exposed
      bound fired after 3271 ms: ExposedSQLException sqlState=57014 ERROR: canceling statement due to statement timeout
      pool still usable: yes
  the same insert as raw JDBC on the same pool, bypassing Exposed
      bound fired after 3313 ms: PSQLException sqlState=57014 ERROR: canceling statement due to statement timeout
      pool still usable: yes

Blocker rolled back. Committed rows: 1 - anything abandoned above was still
queued at that moment, so it lands here: that is what an unbounded wait costs.
```

## The finding: two settings in production code do nothing

`addDataSourceProperty("socketTimeout", 3)` is silently ignored. The same value as `"3"` works.
`connectTimeout` behaves identically. `PostgresPersistence` passes both as `Int` constants, so
neither driver bound is in effect — and the KDoc above them says they are the ones that actually
stop a hung connection.

The mechanism is visible in the third block, and it is a two-library interaction neither library
documents as a hazard. HikariCP keeps `dataSourceProperties` in a `java.util.Properties` and, as
of 6.3.1, copies them with `putAll` rather than `setProperty(key.toString(), value.toString())`
([commit f738486](https://github.com/brettwooldridge/HikariCP/commit/f7384861f6d10363de6573e2cafd1e0f47cdaae6),
done so a Snowflake `PrivateKey` object could survive the trip). We are on 7.1.0, so an `Int`
stays an `Integer` — and `Properties.getProperty`, which is how pgjdbc reads its settings,
returns `null` for any value that is not a `String`. The entry is present; it is simply invisible
to the reader. No warning, no error, on either side.

Worth naming plainly: this configuration was almost certainly correct when it was written
against an older HikariCP, and an upgrade turned it into a no-op without touching the code.

## What each bound actually does, when it does work

**`socketTimeout` kills the connection, not the statement** — `08006`, an I/O error. The
statement is gone because the socket is gone, and the pool has to build a new connection. It is a
backstop for a dead network, not a way to bound a slow query.

**`defaultQueryTimeout` cancels cleanly and the pool stays usable** — `57014`, "canceling
statement due to *user request*", which is pgjdbc's cancel protocol rather than a server setting.
But it covers only statements Exposed issues: the same insert sent as raw JDBC on the same pool
was still waiting at 8 s. Anything not going through Exposed — a health probe, Flyway, a
hand-written query — is unbounded.

**`statement_timeout` bounds everything on the connection** — `57014` again, this time
"canceling statement due to *statement timeout*", for both the Exposed insert and the raw one,
with the pool usable afterwards. It needs no client participation and names its own cause in the
error message.

So the original conclusion holds, for a sharper reason than the one first written: the
client-side bound that works is real but partial, and the one relied on most is not in effect at
all.

## A wrong turn worth keeping

The first version of this spike used `this.password = password` inside `HikariConfig().apply {}`,
where `password` is also the property being assigned — so it assigned the property to itself and
the pool failed with "SCRAM authentication requested but no password". Kotlin's `apply` plus a
file-level `val` of the same name is a shadowing trap; the file-level values are now `dbUser` and
`dbPassword`.

The second version hung for five minutes on the very case that turned out to be the finding,
because its only bound was the one being tested. The third put the bound outside the attempt — a
daemon thread that can be abandoned — which is what made "no bound fired" a printable answer.
Both earlier spikes in this directory hung the same way; this is the third time, and now the
pattern is a rule rather than a habit.
