import type { ApiRequest, ApiResponse, Transport } from "./ApiRequest";

const API_PREFIX = "/api/v1";
const JSON_TYPE = "application/json";

export interface FetchTransportOptions {
    /** Empty in a browser, where the API is on the page's own origin; the backend's address on a server. */
    readonly origin: string;
    /** Sent with every request. A server forwards the visitor's `cookie` and `host` here, and nothing else. */
    readonly headers?: Readonly<Record<string, string>>;
    readonly fetcher?: typeof fetch;
}

/** The last link of the chain: turns a request into one `fetch` and reads the answer. Judges nothing. */
export class FetchTransport implements Transport {
    private readonly origin: string;
    private readonly headers: Readonly<Record<string, string>>;
    private readonly fetcher: typeof fetch;

    public constructor({ origin, headers = {}, fetcher = (input, init) => fetch(input, init) }: FetchTransportOptions) {
        this.origin = origin;
        this.headers = headers;
        this.fetcher = fetcher;
    }

    public async send(request: ApiRequest): Promise<ApiResponse> {
        const hasBody = request.body !== undefined;
        const response = await this.fetcher(`${this.origin}${API_PREFIX}${request.path}`, {
            method: request.method,
            credentials: "same-origin",
            cache: "no-store",
            headers: {
                accept: `${JSON_TYPE}, application/problem+json`,
                ...(hasBody ? { "content-type": JSON_TYPE } : {}),
                ...this.headers,
                ...request.headers,
            },
            ...(hasBody ? { body: JSON.stringify(request.body) } : {}),
        });
        return { status: response.status, body: await this.read(response), headers: response.headers };
    }

    private async read(response: Response): Promise<unknown> {
        const text = await response.text();
        if (text === "") {
            return undefined;
        }
        return response.headers.get("content-type")?.includes("json") === true ? this.parse(text) : text;
    }

    private parse(text: string): unknown {
        try {
            return JSON.parse(text) as unknown;
        } catch {
            return text;
        }
    }
}
