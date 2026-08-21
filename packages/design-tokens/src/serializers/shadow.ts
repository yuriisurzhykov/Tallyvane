export interface ShadowLayer {
    readonly x: number;
    readonly y: number;
    readonly blur: number;
    readonly spread: number;
    readonly color: string;
    readonly inset?: boolean;
}

export function serializeShadow(layers: readonly ShadowLayer[]): string {
    return layers
        .map((layer) => [layer.inset ? "inset" : null, `${String(layer.x)}px`, `${String(layer.y)}px`, `${String(layer.blur)}px`, `${String(layer.spread)}px`, layer.color]
            .filter((part) => part !== null)
            .join(" "))
        .join(", ");
}
