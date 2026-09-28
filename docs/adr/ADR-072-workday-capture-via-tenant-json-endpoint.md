# ADR-072. Workday capture goes through a dedicated server-side `JobSource`, not the extension

## Decision

`capture` gains `WorkdayJobSource`: a server-side `JobSource` implementation, not a route through the
browser extension. `supports` matches `*.myworkdayjobs.com` (and its regional variants, `wd1`–`wd12`).
`fetch` derives four values from the specific job-posting URL the user is capturing — `host`, `tenant`,
`site`, and `externalPath` — and issues `GET https://{host}/wday/cxs/{tenant}/{site}{externalPath}` with
`Accept: application/json`. No login, no session cookie, no API key.

This is the same endpoint the Workday careers page itself calls to render the job the user is looking at;
without the `Accept: application/json` header the identical URL returns the HTML shell instead. Verified
2026-09-01 against live tenants (NVIDIA, Salesforce, Adobe): the response carries `jobPostingInfo` with
`jobDescription` (HTML), `location`, `startDate`, `jobReqId` — everything `ExtractionPipeline` needs as
input, no HTML scraping and no headless browser required.

A blocked or rate-limited request (Workday tenants run Akamai bot management, and behaviour differs by
tenant) is not a new failure mode the port has to grow a case for — it is exactly
`FetchOutcome.Unavailable(reason, retryable = true)`, the shape every other `JobSource` already reports
network trouble through.

## Why

Workday is not LinkedIn, and treating it like LinkedIn would be solving the wrong problem. LinkedIn's
server-side path is refused by ADR-007 for a specific reason: parsing it server-side means an authenticated
session, and an authenticated session on a platform whose terms forbid automation risks the owner's own
account being suspended. That risk does not exist for a Workday careers page — the endpoint answers the
same anonymous request the browser itself sends, with no account and nothing to lose behind it. The
`JobSource` port's own contract note already says `fetch` "does not know whether the result will be
saved" and treats the source as a stateless, unauthenticated data pull; Workday fits that shape exactly,
Greenhouse and Lever's public boards-API endpoints do too, and none of the three needs the extension.

Workday is also not Greenhouse. `GreenhouseJobSource`, `LeverJobSource` and `AshbyJobSource` resolve from a
single guessable slug in the company's own domain — `boards-api.greenhouse.io/v1/boards/{slug}/jobs`. A
Workday address carries three independent, unguessable parts (`host`'s datacenter segment `wd1`–`wd12` is
whichever cluster the customer happened to land on; the site name is free text a Workday admin typed once
and never had to make memorable). There is no directory that maps a company name to its Workday tenant.
That is not a blocker for capture specifically, though, because capture never needs to discover a tenant
from a company name in the first place — the user is always looking at one job-posting URL, and that URL
already contains all three parts plus the `externalPath` needed to fetch that one posting. The "no
registry of tenants" problem that would defeat a market-wide Workday crawler is invisible to a one-URL-at-
a-time capture flow.

## Rejected alternatives

**Route Workday through `ClientSuppliedJobSource`, like LinkedIn.** Considered because Workday, like
LinkedIn, cannot be resolved from a guessable slug. Rejected: the reason LinkedIn needs the extension is
authentication and account risk, and Workday has neither. Sending Workday through the extension anyway
would mean writing and maintaining a `workday/` DOM adapter in `extension/src/adapters/` — selectors that
break on every Workday UI redesign — to reach data a plain, unauthenticated server-side `GET` already
returns as structured JSON. That is strictly more code for a worse result.

**Treat Workday as already covered by `JsonLdJobSource`.** Rejected on a factual basis, not a preference:
many Workday tenant career pages render the job description via client-side JavaScript after the initial
HTML loads, so the page's first response frequently carries no `schema.org/JobPosting` markup for
`JsonLdJobSource` to find — the same reason the generic fallback cannot be assumed to cover every ATS a
user might paste a link from. A dedicated source that speaks the tenant's own JSON API sidesteps the
question of what the static HTML happens to contain.

**Discover Workday tenants automatically from a company domain, matching the other four ATS sources.**
Rejected as solving a problem `capture` does not have. Automatic discovery matters to a market-wide
scraper enumerating every posting at a company; `capture` only ever receives one job-posting URL at a
time, already containing everything `WorkdayJobSource` needs. Building a lookup table three unguessable
parts would exist to serve a use case (`ADR-040`'s rejected market aggregation) this product explicitly
does not have.
