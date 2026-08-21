export interface GradientStop { readonly color: string; readonly position: number; readonly opacity?: number }
export interface LinearGradient { readonly type: "linear"; readonly angle: number; readonly stops: readonly GradientStop[] }
export interface RadialGradient { readonly type: "radial"; readonly position: string; readonly shape?: "circle" | "ellipse"; readonly stops: readonly GradientStop[] }
export interface ConicGradient { readonly type: "conic"; readonly angle?: number; readonly position?: string; readonly stops: readonly GradientStop[] }
/** Several gradients stacked as independent `background-image` layers (comma-joined) — what makes a "mesh" cover read as several organic color zones instead of one blurred blend; a single gradient's stop list can't express that. */
export interface LayeredGradient { readonly type: "layered"; readonly layers: readonly SingleGradient[] }
export type SingleGradient = LinearGradient | RadialGradient | ConicGradient;
export type Gradient = SingleGradient | LayeredGradient;

/** `stop.color` arrives here ALREADY resolved (a real hsl() string, or the color-mix(...) an alpha() reference compiled to) — this function never sees a raw `{reference}`; resolveTree already ran. */
function serializeStop(stop: GradientStop): string {
    if (stop.opacity !== undefined && stop.opacity !== 1) {
        return `color-mix(in srgb, ${stop.color} ${String(stop.opacity * 100)}%, transparent) ${String(stop.position)}%`;
    }
    return `${stop.color} ${String(stop.position)}%`;
}

function serializeSingleGradient(gradient: SingleGradient): string {
    const stops = gradient.stops.map(serializeStop).join(", ");
    switch (gradient.type) {
        case "linear":
            return `linear-gradient(${String(gradient.angle)}deg, ${stops})`;
        case "radial":
            return `radial-gradient(${gradient.shape ?? "ellipse"} at ${gradient.position}, ${stops})`;
        case "conic":
            return `conic-gradient(from ${String(gradient.angle ?? 0)}deg at ${gradient.position ?? "50% 50%"}, ${stops})`;
    }
}

export function serializeGradient(gradient: Gradient): string {
    return gradient.type === "layered" ? gradient.layers.map(serializeSingleGradient).join(", ") : serializeSingleGradient(gradient);
}

function validateSingleGradientStops(name: string, gradient: SingleGradient): void {
    let lastPosition = -1;
    for (const stop of gradient.stops) {
        if (stop.position < 0 || stop.position > 100) {
            throw new Error(`Gradient "${name}" has a stop position out of 0..100: ${String(stop.position)}`);
        }
        if (stop.position < lastPosition) {
            throw new Error(`Gradient "${name}" has out-of-order stops (${String(lastPosition)} then ${String(stop.position)})`);
        }
        lastPosition = stop.position;
        if (stop.opacity !== undefined && (stop.opacity < 0 || stop.opacity > 1)) {
            throw new Error(`Gradient "${name}" has an opacity out of 0..1: ${String(stop.opacity)}`);
        }
    }
}

/** DS005 (gradient-specific) — stop positions ordered within 0..100, opacity within 0..1. Runs against the UNRESOLVED recipe (positions/opacity are plain numbers, not references, so this needs no registry). */
export function validateGradientStops(gradients: Readonly<Record<string, Gradient>>): void {
    for (const [name, gradient] of Object.entries(gradients)) {
        if (gradient.type === "layered") {
            gradient.layers.forEach((layer, index) => { validateSingleGradientStops(`${name}[${String(index)}]`, layer); });
        } else {
            validateSingleGradientStops(name, gradient);
        }
    }
}
