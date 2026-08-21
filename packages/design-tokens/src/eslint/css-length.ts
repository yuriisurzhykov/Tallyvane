/**
 * Shared length-string classification for the dimension ESLint rules.
 * A value is a resource when it goes through `var`/`calc`/`clamp`/`min`/
 * `max`, or is a `ch` measure. Anything else carrying a CSS length unit is
 * a hardcoded dimension — including a named constant that happens to hold
 * `"26px"`.
 */

export const BARE_LENGTH = /^-?[\d.]{1,10}(?:px|rem|em|vh|vw|vmin|vmax|%)$/;
export const LENGTH_IN_TEXT = /[\d.]{1,10}(?:px|rem|em|vh|vw|vmin|vmax|%)/;
export const UNIT_TOKEN = /^(?:px|rem|em|vh|vw|vmin|vmax|%)/;
export const CH_MEASURE = /^-?[\d.]{1,10}ch$/;
const RESOURCE_FN = /\b(?:var|calc|clamp|min|max)\(/;

export function isResourceCssValue(value: string): boolean {
    const trimmed = value.trim();
    return CH_MEASURE.test(trimmed) || RESOURCE_FN.test(trimmed);
}

/** True for `"26px"`, `"0 0 32px"`, `" 1.5rem "`. False for `var()`, `calc()`, `ch`, `"0 0 auto"`. */
export function isHardcodedCssLength(value: string): boolean {
    if (isResourceCssValue(value)) return false;
    return LENGTH_IN_TEXT.test(value);
}

/**
 * Properties where a unitless number is a React pixel (`height: 32` →
 * `32px`). `flex`/`lineHeight`/`zIndex` are unitless on purpose and stay
 * off this list.
 */
export const REACT_PX_PROPERTY =
    /^(width|height|minWidth|minHeight|maxWidth|maxHeight|padding|padding(Top|Right|Bottom|Left|Inline|InlineStart|InlineEnd|Block|BlockStart|BlockEnd)?|margin|margin(Top|Right|Bottom|Left|Inline|InlineStart|InlineEnd|Block|BlockStart|BlockEnd)?|gap|(row|column)Gap|top|right|bottom|left|inset|inset(Inline|Block)?|fontSize|borderRadius|border(TopLeft|TopRight|BottomLeft|BottomRight)Radius|flexBasis)$/;

export const DIMENSION_PROPERTY =
    /^(width|height|minWidth|minHeight|maxWidth|maxHeight|padding|padding(Top|Right|Bottom|Left|Inline|InlineStart|InlineEnd|Block|BlockStart|BlockEnd)?|margin|margin(Top|Right|Bottom|Left|Inline|InlineStart|InlineEnd|Block|BlockStart|BlockEnd)?|gap|(row|column)Gap|top|right|bottom|left|inset|inset(Inline|Block)?|fontSize|borderRadius|border(TopLeft|TopRight|BottomLeft|BottomRight)Radius|flex|flexBasis|transform|translate|gridTemplateColumns)$/;

export function isDimensionPropertyKey(keyName: string): boolean {
    return DIMENSION_PROPERTY.test(keyName) || keyName.startsWith("--");
}
