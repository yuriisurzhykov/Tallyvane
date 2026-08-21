/**
 * JS pixel seed for `useVirtualizer.estimateSize` only — painted row
 * height is `var(--control-height-sm)`. The engine cannot read a CSS
 * variable; `measureElement` on each row corrects drift. See ADR-042.
 *
 * @architecture-exception rule=no-raw-dimension-value adr=ADR-042
 *   reason=useVirtualizer.estimateSize cannot read a CSS variable
 */
export const ROW_HEIGHT_PX = 32;

/** Rows just outside the viewport that stay mounted, so an arrow key landing one row past the fold does not need to wait a full re-measure before it can be focused. */
export const OVERSCAN = 8;
