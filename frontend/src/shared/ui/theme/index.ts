/**
 * Public API of the theme.
 *
 * Deliberately narrow. Almost everything this directory produces reaches the
 * application as CSS custom properties through `adapters/tailwind.css`, not as
 * imports — so there is nothing here for a component to pull in, and that is
 * the design rather than an omission.
 *
 * The one export is the resolved token data, for consumers with no CSS engine
 * to lean on: anything drawing to a canvas, an image or a chart. They read
 * already-resolved values and never the token source, which is what keeps the
 * compiler out of the bundle.
 */
export { resolved } from "./generated/resolved";
