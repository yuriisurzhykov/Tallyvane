# ADR-080. The browser carries the session in a `__Host-` cookie on `app.`, and forgery is refused by origin

## Status

Accepted. Amends §17, which called for an anti-forgery token on unsafe methods.

## Context

The API has two entrances into one backend. `api.<domain>` serves the extension and the mobile
client, which send `Authorization: Bearer` (ADR-081). The browser reaches the same backend through
`app.<domain>/api/`, which nginx already proxies (`ops/nginx/templates/20-app.conf.template`).

The browser entrance stays on `app.` because `app.` and `api.` are one site but two origins. A
cookie set by `api.` is never sent to `app.`, so server rendering of signed-in pages would not see
the session. A cookie for the whole domain would reach `admin.` and the public site, which ADR-032
forbids. And `fetch` from `app.` to `api.` with credentials would need CORS and a preflight on every
change.

A cookie the browser attaches by itself opens the door to cross-site request forgery: another site
makes the victim's browser send a request to us, and the browser adds the cookie.

## Decision

**The cookie is `__Host-session`**, with `Secure`, `HttpOnly`, `Path=/`, no `Domain`, and
`SameSite=Lax`. The `__Host-` prefix makes the browser refuse the cookie unless it is host-only and
secure, so it cannot be shared with or overwritten from a neighbouring subdomain. `HttpOnly` keeps
it away from scripts. `Lax` sends it on an ordinary link click, so a link from an email does not
land the person signed out, and withholds it from cross-site `POST`, `fetch` and frames.

**Sessions on `app.` and `admin.` are separate** (§11.2, ADR-032): a stolen console session does
not open the admin.

**Forgery is refused by origin, on every unsafe method.** The request must carry `Origin` equal to
exactly `https://app.<domain>`, or, when `Origin` is absent, `Sec-Fetch-Site: same-origin`. A
request with neither header is refused. The comparison is to the exact origin, not to "same site":
same-site would let a script on the public site or the blog through.

**The API accepts only `application/json` bodies** on unsafe methods. An HTML form cannot send that
content type, and a cross-origin script that tries needs a preflight, which this server does not
grant.

**The `Bearer` entrance is exempt from both checks**, because nothing there is attached by the
browser on its own.

**A strict Content Security Policy without `unsafe-inline` for scripts.** Cross-site scripting is
what defeats every measure above: a script cannot read an `HttpOnly` cookie, but it can make
requests from the page itself.

## Alternatives considered

**`SameSite=Strict`.** Rejected: a person following a link to `app.` from an email or a chat would
arrive looking signed out, because the cookie is not sent on the first cross-site navigation.

**A synchronizer or double-submit token.** The classic defence, and it works in very old browsers,
but the front end must fetch, store and attach it on every change. OWASP recognises the origin check
with Fetch Metadata as a complete defence on its own. The previous attempt's `DoubleSubmitGuard` is
not carried over.

**Both.** Rejected as cost without a threat that needs it, given `SameSite=Lax` already removes most
of the attack.

**Only `api.`, pages rendered without a session.** Loses server rendering of signed-in pages.

**A cookie for the whole domain.** Forbidden by ADR-032.
