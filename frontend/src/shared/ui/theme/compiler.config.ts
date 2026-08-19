import type { CompilerInput } from "design-token-engine";
import { colorContract, layoutContract, radiusContract, spacingContract, typographyContract, zIndexContract } from "./contracts";
import { border, color, dimension, layout, motion, radius, typography, zIndex } from "./tokens";
import { darkTheme, lightTheme } from "./themes";
import { layoutRole, radiusRole, spacingRole, typographyRole, zIndexRole } from "./semantic";
import { statusChipTokens, timelineConnectorTokens } from "./components";
import { shadows, textStyles, transitions } from "./composites";

/**
 * Assembly, and nothing else. Every object below was already validated at the
 * moment its module was imported, so there is deliberately no logic here to
 * read past the wiring.
 *
 * Light is listed before dark because the first theme compiles to `:root` and
 * the rest to `.theme-<name>` overrides. Reordering these two lines silently
 * changes which theme a page falls back to before any class is applied.
 *
 * `themes` versus `flatSemantics` is the one distinction worth understanding
 * here: colour is the only category with a theme axis, so it is the only one
 * that gets a tree per theme. Radius and spacing mean the same thing in both,
 * and go in flat — same validation, same contracts, one set of variables.
 */
const compilerInput: CompilerInput = {
    // `z`, not `zIndex` — the category name becomes the variable prefix, and
    // `--ds-semantic-z-modal` is what the class-facing `z-modal` lines up with.
    primitives: { color, dimension, radius, border, typography, motion, layout, z: zIndex },
    contracts: {
        color: colorContract,
        radius: radiusContract,
        spacing: spacingContract,
        typography: typographyContract,
        layout: layoutContract,
        z: zIndexContract,
    },
    themes: {
        light: { color: lightTheme },
        dark: { color: darkTheme },
    },
    flatSemantics: {
        radius: radiusRole,
        spacing: spacingRole,
        typography: typographyRole,
        layout: layoutRole,
        z: zIndexRole,
    },
    components: [statusChipTokens, timelineConnectorTokens],
    composites: [shadows, textStyles, transitions],
};

export default compilerInput;
