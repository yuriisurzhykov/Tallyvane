import { definePrimitives } from "design-token-engine";

/**
 * The spacing scale. Base unit four, rhythm eight. A value outside this scale
 * is a build error rather than a matter of taste — `no-raw-dimension-value`
 * and `no-arbitrary-dimension-class` report every bare literal, with no
 * threshold and no exemption for "too small to matter".
 *
 * The key is the size in quarters of the base: key 4 is 16px is `1rem`. Values
 * carry their unit rather than being bare numbers, because the compiler emits
 * each one verbatim as a custom property and Tailwind's `--spacing-*` needs a
 * real length there — a unitless `16` is not one, and produces a rule the
 * browser discards without complaint.
 *
 * `rem`, not `px`, so the whole scale responds to the reader's own font size.
 */
export const dimension = definePrimitives({
    0: "0",
    1: "0.25rem",
    2: "0.5rem",
    3: "0.75rem",
    4: "1rem",
    5: "1.25rem",
    6: "1.5rem",
    8: "2rem",
    10: "2.5rem",
    12: "3rem",
    16: "4rem",
    20: "5rem",
    24: "6rem",
    32: "8rem",
});
