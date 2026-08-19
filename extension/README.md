# extension

A Chrome extension, Manifest V3. It exists for exactly one reason: LinkedIn
cannot be parsed from a server. Login walls, anti-bot measures and the account
risk make it a dead end, while ATS platforms like Greenhouse and Lever expose
public JSON and parse server-side easily.

So there are two capture paths behind one interface, and this is the browser
half of it.

```
src/
├── background/   service worker: send queue, authorisation
├── content/      injected scripts
├── adapters/     one per site, behind a shared SiteAdapter interface
│   ├── linkedin/
│   ├── greenhouse/
│   └── generic/
├── ui/           popup panel
└── api/          client generated from docs/openapi.yaml
```

## What it does not do

It reads pages the user already has open. It does not automate anything, does
not act on the user's behalf on a third-party platform, and does not drive a
headless browser with their cookies.

## Selectors are the fragile part

Every adapter keeps its DOM selectors in one place, separate from its logic,
because selectors are what breaks when someone else redesigns their site. When
extraction fails the adapter returns nothing rather than guessing, the
extension says so plainly, and offers to capture the page as raw text instead.

## Delivery is queued

Captures go into a local queue first. If the server is unreachable nothing is
lost, and each item carries a deduplication key so a retry after a failure
cannot create a second copy.

## Not FSD

This tree does not follow Feature-Sliced Design, and that is deliberate: it is
not an application with screens, it is a set of adapters. The isolation rule
still holds in its own form — adapters never import each other and meet only
through the registry and the shared interface.
