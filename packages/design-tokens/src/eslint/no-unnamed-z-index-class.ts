/**
 * The one hole a Tailwind theme cannot close from CSS.
 *
 * `z-10`, `z-50` and `z-[9999]` are bare-value utilities: Tailwind builds them
 * arithmetically without consulting the theme, so clearing the `--z-*`
 * namespace removes nothing and registering named layers adds an alternative
 * rather than a replacement. The only place left to enforce the named set is
 * where the class is written.
 *
 * Why it matters more than the usual "use a token" argument: a stacking number
 * wins an argument the rest of the interface was never told about. Whoever
 * writes `z-50` cannot know what else is at 50, the layer that loses is found
 * by someone else weeks later, and the fix is always another, larger number.
 * A named layer forces the ordering to be decided once, in a file where the
 * whole ladder is visible.
 *
 * Named classes pass untouched — `z-modal`, `z-tooltip`. So do the keywords
 * Tailwind provides for the two positions that carry no ordering at all,
 * `z-auto` and negative `-z-*`... which is to say `-z-` is NOT exempt: a
 * negative layer is still a layer.
 */
import type { Rule } from "eslint";
import { isClassNameAttribute, walkForStrings } from "./ast-helpers.ts";

/**
 * Fully anchored and tested per whitespace-delimited token, matching the other
 * class-facing rules in this package — the shape a real ReDoS finding forced on
 * the first of them, never re-derived from scratch here.
 *
 * `(?:[\w-]{1,20}:)?` allows one leading modifier, so `lg:z-40` is caught the
 * same as `z-40`.
 */
const UNNAMED_Z_CLASS = new RegExp("^(?:[\\w-]{1,20}:)?-?z-(?:\\d{1,6}|\\[[^\\]]{1,40}\\])$");

function findUnnamedZClasses(value: string): string[] {
    return value.split(/\s+/).filter((token) => UNNAMED_Z_CLASS.test(token));
}

const rule: Rule.RuleModule = {
    meta: {
        type: "problem",
        docs: {
            description:
                "Disallow a numeric or arbitrary z-index utility (z-10, z-[9999]) — use a named stacking layer from the project's own scale instead, so the order is decided in one place rather than argued a number at a time.",
        },
        schema: [],
        messages: {
            unnamedZIndex:
                '"{{match}}" sets a stacking order by number. Use a named layer instead: the number wins against layers it was never compared with, and the next person raises it.',
        },
    },
    create(context) {
        return {
            JSXAttribute(node: any) {
                if (!isClassNameAttribute(node)) return;
                walkForStrings(node.value, (value, stringNode) => {
                    for (const match of findUnnamedZClasses(value)) {
                        context.report({ node: stringNode, messageId: "unnamedZIndex", data: { match } });
                    }
                });
            },
        };
    },
};

export default rule;
