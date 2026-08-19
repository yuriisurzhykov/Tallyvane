import { definePrimitives } from "design-token-engine";

/**
 * Border widths, kept apart from the spacing scale on purpose. A line's
 * thickness and a gap between things are unrelated quantities that happen to be
 * measured in the same unit, and folding them together is what leads to a
 * hairline drawn four pixels thick because the spacing scale had no smaller
 * step — it correctly has none, since nothing should ever be spaced by a pixel.
 *
 * Two values, and the interface needs no third: every border here is one pixel,
 * and two is reserved for the focus ring, where the extra weight is doing
 * accessibility work rather than decoration.
 *
 * In `px`, unlike every other length in this system. A hairline that scales
 * with the reader's font size stops being a hairline; on a device with
 * fractional pixel ratios it lands between physical pixels and renders as a
 * blurred grey band instead of a line.
 */
export const border = definePrimitives({
    hairline: "1px",
    focus: "2px",
});
