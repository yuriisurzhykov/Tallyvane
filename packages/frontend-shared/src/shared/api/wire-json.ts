function mapJsonKeys(value: unknown, mapKey: (key: string) => string): unknown {
    if (Array.isArray(value)) return value.map(item => mapJsonKeys(item, mapKey));
    if (value === null || typeof value !== "object") return value;
    return Object.fromEntries(Object.entries(value).map(([key, nested]) => [mapKey(key), mapJsonKeys(nested, mapKey)]));
}

export function toWireJson(value: unknown): unknown {
    return mapJsonKeys(value, key => key.replace(/[A-Z]/g, letter => `_${letter.toLowerCase()}`));
}

export function fromWireJson(value: unknown): unknown {
    return mapJsonKeys(value, key => key.replace(/_([a-z])/g, (_match, letter: string) => letter.toUpperCase()));
}
