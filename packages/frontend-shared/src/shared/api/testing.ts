import type { ApiRequest, ApiResponse, Transport } from "./ApiRequest";

/** Answers each request from a script and remembers what it was asked. Shared by this folder's tests. */
export class ScriptedTransport implements Transport {
    public readonly requests: ApiRequest[] = [];
    private readonly answers: (ApiResponse | Error)[];

    public constructor(...answers: (ApiResponse | Error)[]) {
        this.answers = answers;
    }

    public send(request: ApiRequest): Promise<ApiResponse> {
        this.requests.push(request);
        const answer = this.answers.shift() ?? ok(undefined);
        return answer instanceof Error ? Promise.reject(answer) : Promise.resolve(answer);
    }
}

export function ok(body: unknown, status = 200): ApiResponse {
    return { status, body, headers: new Headers() };
}

export function problem(kind: string, status: number, headers: Record<string, string> = {}): ApiResponse {
    return {
        status,
        body: { type: `https://tallyvane.com/errors/${kind}`, title: kind, status },
        headers: new Headers(headers),
    };
}

export function request(method: ApiRequest["method"], path: string, headers: Record<string, string> = {}): ApiRequest {
    return { method, path, headers };
}
