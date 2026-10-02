export type HttpMethod = "GET" | "POST" | "PUT" | "PATCH" | "DELETE";

/** What a caller asks for. Header names are lower case, so two layers cannot disagree about spelling. */
export interface ApiRequest {
    readonly method: HttpMethod;
    /** Relative to the API prefix, exactly as `docs/openapi.yaml` spells it: `/me`, not `/api/v1/me`. */
    readonly path: string;
    readonly body?: unknown;
    readonly headers: Readonly<Record<string, string>>;
}

/** What came back, already read: `body` is the parsed JSON, the raw text, or `undefined` when there was none. */
export interface ApiResponse {
    readonly status: number;
    readonly body: unknown;
    readonly headers: Headers;
}

/**
 * The one seam of the API client. Every layer between a feature and the network implements it and holds
 * the next one, so each layer owns one concern and none of them knows the others exist.
 */
export interface Transport {
    send(request: ApiRequest): Promise<ApiResponse>;
}
