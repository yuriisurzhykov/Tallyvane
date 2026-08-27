# http

## 2026-08-26 — what does the API actually answer, over a real socket?

Slice 11 built the HTTP layer and eleven tests on Ktor's test host, which runs the application
in-process and never opens a port. That leaves two things unproven: whether it serves a real
client at all, and whether the correlation between a response and its log lines works outside a
test harness. `app` does not exist yet and CIO was a test-only dependency, so there was no way to
send a request by hand.

```bash
./gradlew :playground:http:run                        # port 8099
./gradlew :playground:http:run "-Pspike.port=9000"    # quote the -P in PowerShell
```

Launching it twice used to produce a `JobCancellationException` with forty lines of coroutine
internals, a `BindException` at the bottom, and a cheerful "Listening on …" banner printed before
any of it — reported from a second launch while the first was still up. The port is checked before
Ktor starts now, and a busy one says so in three lines.

The menu it prints is the list of cases, one command each — copy them from the running process
rather than from here, because they are spelled for the shell that is actually running. Watch the
log lines as well as the responses: the point is that the two carry the same id.

**Why the menu adapts.** In PowerShell `curl` is an alias for `Invoke-WebRequest`, which does not
understand `-i` and answers by prompting for a `Uri` — so the menu prints `curl.exe` on Windows. And
PowerShell rewrites quotes when handing arguments to a native program, inside single quotes too: a
body written `-d '{"a":1}'` arrives mangled and the endpoint answers 400, which is indistinguishable
from a genuinely broken body. So the JSON body is printed with its inner quotes backslash-escaped on
Windows. Both forms were checked against the running server; a body read from a file
(`--data-binary "@file.json"`) works as well.

## What the run showed

```
$ curl -i localhost:8099/api/v1/probes/ok
HTTP/1.1 200 OK
traceparent: 00-01a03d11437576b887f2347a589d6249-b145f1784be42d16-01
Content-Type: application/json

{"take_home_cents":51200,"paid_on":"2026-08-31"}
```

and the line the handler itself wrote, in the same second:

```
{"level":"INFO","loggerName":"tallyvane.playground.http.Probes",
 "mdc": {"span_id":"b145f1784be42d16","trace_id":"01a03d11437576b887f2347a589d6249"},
 "formattedMessage":"Answering the happy path"}
```

Same `trace_id` in the header and in the log. That is the whole promise of ADR-056, and this is the
first time it has been seen over a socket rather than asserted.

```
$ curl -i localhost:8099/api/v1/probes/refused
HTTP/1.1 422 Unprocessable Entity
Content-Type: application/problem+json

{"type":"https://tallyvane.com/errors/validation-failed","title":"Validation failed","status":422,
 "detail":"Minimum exceeds maximum","errors":[{"field":"salary_min_cents","code":"range.invalid"}],
 "trace_id":"01a03d116d0072919f3cd02912c2b2bf"}
```

```
$ curl -i localhost:8099/api/v1/nowhere
HTTP/1.1 404 Not Found
Content-Type: application/problem+json

{"type":"about:blank","title":"Not Found","status":404,"trace_id":"…"}
```

`about:blank` since the fix recorded in `platform/http/README.md`: an unmatched path is a failure with
no meaning beyond its code, and a module's own 404 keeps its own `type` and its `detail`. Before that
fix both answers were identical, which is how the defect was found.

```
$ curl -i -X POST localhost:8099/api/v1/probes/echo -H 'content-type: application/json' -d '{ oops'
HTTP/1.1 400 Bad Request
{"type":"https://tallyvane.com/errors/malformed-request","title":"Malformed request","status":400,
 "detail":"The request body could not be read as JSON matching this endpoint."}
```

Sending a `traceparent` continues that trace rather than starting one — the log line from inside
the handler carried the *caller's* id, `4bf92f3577b34da6a3ce929d0e0e4736`, not one of ours.

## The defect this spike found

`/boom` throws an exception whose message holds a password. The response was correct — 500, no
detail, nothing about the driver — but:

```
{"level":"ERROR","loggerName":"tallyvane.platform.http.Api","mdc": {},
 "formattedMessage":"Request failed", …}
```

**`mdc` empty, and no `trace_id` in the body either.** So in the one case where a user quotes an id
from their screen, there was no id — not in the body, and not on the log line you would search
with. An exception unwinds past the `withContext` that carried the trace, so by the time
`StatusPages` handles it the context element is gone.

Fixed by putting the trace in the call's *attributes* as well, which the unwinding does not touch,
and by restoring it explicitly around the error log. After the fix, the same request:

```
traceparent: 00-01a03d15357176a2805d3a21024ac16b-a4a32c2d38085ae5-01
{"…","status":500,"trace_id":"01a03d15357176a2805d3a21024ac16b"}

{"level":"ERROR","mdc": {"span_id":"a4a32c2d38085ae5","trace_id":"01a03d15357176a2805d3a21024ac16b"},
 "formattedMessage":"Request failed"}
```

Header, body and log line agree. `ApiSpec` now pins it, so it cannot regress.

## Two wrong turns worth keeping

A `curl` with the JSON body written inline came back 400 — the same answer as a genuinely malformed
body — and it looked like snake_case was written but not read. It was PowerShell mangling the quotes:
the same body from a file round-tripped correctly. The real finding is smaller and still worth
having: nothing in the suite asserted that a valid snake_case body is *read*, only that a broken one
is refused. That case exists now.

The menu itself then made the same mistake in the opposite direction. It printed `curl`, which in
PowerShell is `Invoke-WebRequest` and answers by asking for a `Uri`; and it printed the body in plain
single quotes, which PowerShell mangles — so the one command meant to demonstrate that snake_case is
read returned 400 when run from the menu verbatim. Both are now spelled per host shell, and each
printed command was run exactly as printed before this file was written. A spike whose own
instructions do not work is worse than no spike: it teaches the wrong lesson confidently.

## 2026-08-26 — does Ktor's `CallLogging` line carry the trace?

ADR-056 left an access log open, and the open question was not whether the plugin logs — it does —
but whether its line can be joined to a trace. The doubt came from the defect above: an exception
unwinds past the context element, and `CallLogging` logs after the response is sent, which is the same
neighbourhood. So the plugin was installed here with no `mdc` hook at first, to see what reaches the
line on its own.

```
GET /probes/ok    "mdc": {"span_id":"98825cb416b7abb3","trace_id":"01a03d87910973d683cbf91af3deb388"}
GET /probes/boom  "mdc": {}
GET /nowhere      "mdc": {"span_id":"b686948e34150867","trace_id":"01a03d87920f70d28d2f6954935af6dd"}
```

The ambient ids reach the line for a 200 and for Ktor's own 404, and are **gone for the 500** — the
one line most worth correlating is the one that cannot be. Same root cause as the defect above, and
the fix that repaired the response body does not repair this: the body reads the call's attributes,
and the plugin reads the thread.

The second half of the measurement asked whether the plugin's own hook is a way out, since
`mdc(key) { call -> … }` reads from the call:

```
GET /probes/ok    "mdc": {"from_call":"/api/v1/probes/ok",   "span_id":"a9d46ea9…","trace_id":"01a03d8999dc…"}
GET /probes/boom  "mdc": {"from_call":"/api/v1/probes/boom"}
```

`from_call` survives all three. So an access log **can** be correlated — by being told where the trace
lives, not for free. Both forms are left installed in the spike so the difference stays visible.

What that settles and what it does not: the mechanism is understood, and the cost of an access log is
now known to include exposing the trace from `platform:http` so something outside it can read it. The
question of whether this application wants an access log at all is a separate decision, taken with
the author rather than here.

## What this spike is not

Not a server. The port, the engine's configuration, graceful shutdown and the real route list
belong to `app` in slice 13; here CIO is wired in eight lines to make a socket exist. The `logback.xml`
in its resources stands in for the one `app` will own.
