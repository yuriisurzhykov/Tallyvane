import type { CompilerInput } from "design-token-engine";
import {
    borderContract,
    colorContract,
    layoutContract,
    radiusContract,
    spacingContract,
    typographyContract,
    zIndexContract,
} from "./contracts";
import { border, color, dimension, layout, motion, radius, typography, zIndex } from "./tokens";
import { darkTheme, lightTheme } from "./themes";
import { borderRole, layoutRole, radiusRole, spacingRole, typographyRole, zIndexRole } from "./semantic";
import { statusBadgeTokens, timelineConnectorTokens } from "./components";
import { shadows, textStyles, transitions } from "./composites";

/**
 * Assembly, and nothing else. Every object below was already validated at the
 * moment its module was imported, so there is deliberately no logic here to
 * read past the wiring.
 *
 * Dark is listed before light because the first theme compiles to `:root` and
 * the rest to `.theme-<name>` overrides — and dark is what the first render
 * deterministically produces on both server and client (§12). Reordering these
 * two lines silently changes which theme a page falls back to.
 *
 * `themes` versus `flatSemantics` is the distinction worth understanding here:
 * colour is the only category with a theme axis, so it is the only one that
 * gets a tree per theme. Everything else means the same thing in both and goes
 * in flat — same validation, same contracts, one set of variables.
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
        border: borderContract,
        layout: layoutContract,
        z: zIndexContract,
    },
    themes: {
        dark: { color: darkTheme },
        light: { color: lightTheme },
    },
    flatSemantics: {
        radius: radiusRole,
        spacing: spacingRole,
        typography: typographyRole,
        border: borderRole,
        layout: layoutRole,
        z: zIndexRole,
    },
    components: [statusBadgeTokens, timelineConnectorTokens],
    composites: [shadows, textStyles, transitions],
};

export default compilerInput;
