import type { paths } from "./generated/schema";
import type { ApiRequest, HttpMethod, Transport } from "./ApiRequest";
import { IDEMPOTENCY_KEY_HEADER } from "./IdempotentTransport";

type Verb = "get" | "post" | "delete";

/** The paths of `docs/openapi.yaml` that have an operation for the verb. A typo in a path is a compile error. */
type PathsWith<V extends Verb> = {
    [P in keyof paths]: [NonNullable<paths[P][V]>] extends [never] ? never : P;
}[keyof paths];

type Operation<P extends keyof paths, V extends Verb> = NonNullable<paths[P][V]>;

type SuccessResponse<Op> = Op extends { responses: infer R }
    ? R[Extract<keyof R, 200 | 201 | 202 | 204>]
    : never;

/** What the server answers with on success: the JSON the specification names, or nothing. */
type ResponseBody<Op> = SuccessResponse<Op> extends { content: { "application/json": infer Body } } ? Body : undefined;

type RequestBody<Op> = Op extends { requestBody: { content: { "application/json": infer Body } } } ? Body : never;

export interface CallOptions {
    /** For a caller repeating its own intention, e.g. after the network dropped: the key of the first try. */
    readonly idempotencyKey?: string;
}

type BodiedPostPaths = {
    [P in PathsWith<"post">]: [RequestBody<Operation<P, "post">>] extends [never] ? never : P;
}[PathsWith<"post">];

type BodilessPostPaths = Exclude<PathsWith<"post">, BodiedPostPaths>;

/**
 * What a feature holds to talk to the server: one method per HTTP verb, typed from the specification, and
 * nothing else. It builds a request and hands it to the chain of transports; what happens on the way
 * (keys, failures, an ended session) is not its business and not the feature's.
 */
export class Api {
    private readonly transport: Transport;

    public constructor(transport: Transport) {
        this.transport = transport;
    }

    public get<P extends PathsWith<"get">>(path: P): Promise<ResponseBody<Operation<P, "get">>> {
        return this.call("GET", path, undefined, undefined);
    }

    public post<P extends BodiedPostPaths>(
        path: P,
        body: RequestBody<Operation<P, "post">>,
        options?: CallOptions,
    ): Promise<ResponseBody<Operation<P, "post">>> {
        return this.call("POST", path, body, options);
    }

    /** A `POST` that carries no body: the request is the intention. */
    public trigger<P extends BodilessPostPaths>(
        path: P,
        options?: CallOptions,
    ): Promise<ResponseBody<Operation<P, "post">>> {
        return this.call("POST", path, undefined, options);
    }

    public delete<P extends PathsWith<"delete">>(path: P, options?: CallOptions): Promise<ResponseBody<Operation<P, "delete">>> {
        return this.call("DELETE", path, undefined, options);
    }

    private async call<Body>(method: HttpMethod, path: string, body: unknown, options: CallOptions | undefined): Promise<Body> {
        const request: ApiRequest = {
            method,
            path,
            ...(body !== undefined ? { body } : {}),
            headers: options?.idempotencyKey !== undefined ? { [IDEMPOTENCY_KEY_HEADER]: options.idempotencyKey } : {},
        };
        return (await this.transport.send(request)).body as Body;
    }
}
