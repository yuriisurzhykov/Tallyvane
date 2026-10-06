# qr-code

Tier 0 — a QR code, drawn in the browser as one SVG. The first use is the key a person scans into an
authenticator app when turning TOTP on (slice 5c, ADR-094).

## What needed doing

The server tells a new TOTP key once, as text and as an `otpauth://` address, and people expect to scan it, not to
type thirty-two characters. Nothing in `shared/ui` drew a QR code, and the key must not be sent to a service that does.

## What was actually done

Wraps the `qrcode` library's `create`, which returns the matrix and nothing else, and draws that matrix as one `path`
of unit squares inside an `<svg role="img">` with a required `label`, since the picture has no text of its own.
Taking the matrix instead of the library's SVG or canvas output means no HTML string is injected into the page, the
component renders on the server and in tests without a canvas, and the library is the only part that knows how a code
is laid out. The four-module quiet zone the specification asks for is part of the `viewBox`.

It is always dark on light. A dark page would invert the usual `text-primary` on `surface` pair, and many readers
cannot scan an inverted code, so the element carries the `theme-light` class the theme provider already defines and
takes its two colours from that theme's roles. No colour in the file is a literal.

It has a size of its own, the component token `qrCode.size` (18rem), and never wider than its container: an SVG that has only a `viewBox` fills the width it
is given, and in the first version of the Security page that made the code as wide as the page, too big to see whole
and awkward to scan. A QR code has no useful range of sizes, so the primitive owns it and `className` stays layout only.

## SOLID

Single responsibility: turn a string into a picture of a QR code, and say what the picture is for. Dependency
inversion: only `QrCode.tsx` imports `qrcode`; a different encoder would change that one function and nothing else.
