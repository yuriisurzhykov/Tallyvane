# image

Accessible media. Tier 0.

## Responsibility

`Image` gives every shared image an explicit alternative text and a deliberate
fit policy. It defaults to lazy loading and asynchronous decoding so content
images do not block the main thread or compete with immediately visible UI.
Callers can set `loading="eager"` for an image that must appear in the initial
viewport and choose `fit="cover"` when the image fills a bounded frame.

The component deliberately has no domain knowledge and does not calculate
responsive dimensions. Those decisions belong to the layout that owns the
image; this primitive provides only a safe media element and token-compatible
fit behavior.

## Contract

- `src` and `alt` are required; meaningful images must be described by the
  caller.
- `fit` is `contain` by default and may be set to `cover`.
- `loading` is `lazy` by default and may be set to `eager`.
- Decoding stays asynchronous and the image is not draggable.
- Intrinsic sizing and responsive layout remain controlled by the caller.

## Verification

`Image.test.tsx` checks the accessible name and the loading, decoding, and
dragging defaults.
