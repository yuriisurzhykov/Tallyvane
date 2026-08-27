# i18n

The string lookup factory — `createUseStrings` and its `KeyOf` helper.
ARCHITECTURE.md §13 / ADR-016: every user-facing string is retrieved by
`namespace.key`, never written into JSX. This segment is the mechanism that
does that retrieval. It is not the catalogue.

## Why nothing existing could be reused

`shared/i18n` was an `export {}` stub. The lookup could not live inside a
feature or a view: both apps will need the same typed `{name}` substitution,
and putting the factory in either one would make the other re-derive it.
An off-the-shelf i18n library (ICU messages, plural rules, locale
negotiation) is the thing §13.2 explicitly deferred — "at the current
volume a real plural engine is surplus" — so this is a small generic
function, not a vendor.

## Why the dictionary is not here

ARCHITECTURE.md §13.2's own example (`shared/i18n/locales/en.json` with
`pipeline` / `brief` namespaces) predates ADR-032. Shared has zero domain
knowledge; a dictionary that names Tallyvane features would be the same
leak `content-kit` was extracted to stop. The split is the same one: this
package owns the generic factory, each app owns the JSON and the
`createUseStrings(dictionary)` instantiation.

`frontend-web` wires that instantiation at `src/app/i18n`.
`frontend-admin` will do the same for its own copy when it has strings.

## What was actually done

`createUseStrings(dictionary)` returns `useStrings(namespace)`, which
returns `t(key, vars?)`. Types flow from the dictionary object: an unknown
namespace or key is a compile error (`KeyOf` plus the generic namespace
parameter). Substitution is `String.replaceAll` of `{name}` — no ICU, no
plural category. Missing names in `vars` leave the placeholder in place,
which is the predictable failure rather than a thrown interpolation error
at render.

The returned function is named `useStrings` because that is the name §13
and ADR-016 already use. It is not a React hook: it closes over the
dictionary, uses no state and no context, and is safe to call from a
Server Component. The CMS overlay in §13.3 (`content.strings` merged on
top of the bundled dictionary) is out of scope here — it depends on
`content-kit`, which is still a stub. Adding it later is an overlay in
front of this function, not a rewrite of it.

## SOLID

Single responsibility: resolve `namespace.key` to a string, optionally
substituting `{name}` placeholders. It knows nothing about what any key
means, which language the value is in, or which app is calling. Open/
closed: a new namespace is a new key in the caller's JSON, never a branch
here; a CMS overlay later wraps this function rather than editing it.
Dependency inversion: callers depend on the generic `t(key, vars?)`
shape, not on JSON-module loading or a particular file path — those stay
in the app that owns the dictionary.
