/**
 * A duration paired with the easing that belongs to it.
 *
 * The pairing is the whole reason this layer exists. Durations and easings are
 * separate primitives, and left separate they get combined afresh at every call
 * site — which is how an interface ends up easing one way in one component and
 * another way three files over, with nobody having decided either.
 *
 * Deliberately NOT collapsed into a `transition` shorthand on the way out. Two
 * variables can always be composed into a shorthand where they are used, and
 * that leaves the caller free to say which property is animated; a
 * pre-assembled shorthand cannot be taken apart, and omitting the property from
 * it silently means `all`.
 */
export interface Transition {
    readonly duration: string;
    readonly easing: string;
}

/**
 * Values arrive here already resolved, so a missing half means the recipe was
 * written incomplete. Caught rather than emitted, because a half-declared
 * transition fails in the quietest possible way: the variable is simply absent,
 * the property falls back to its default, and the motion is subtly wrong
 * everywhere that token is used with nothing to point at.
 */
export function validateTransitions(transitions: Readonly<Record<string, Transition>>): void {
    for (const [name, transition] of Object.entries(transitions)) {
        for (const field of ["duration", "easing"] as const) {
            if (typeof transition[field] !== "string") {
                throw new Error(`Transition "${name}" is missing its ${field}`);
            }
        }
    }
}
