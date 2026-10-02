import type { IdempotencyKeys } from "./IdempotencyKeys";
import { RandomIdempotencyKeys } from "./RandomIdempotencyKeys";
import type { ApiRequest, ApiResponse, HttpMethod, Transport } from "./ApiRequest";

export const IDEMPOTENCY_KEY_HEADER = "idempotency-key";

const UNSAFE: readonly HttpMethod[] = ["POST", "PUT", "PATCH", "DELETE"];

/**
 * Gives every request that changes something a key, once (ADR-086). It sits outside the layers that may
 * repeat a request, so a repeat carries the key of the original and the server can recognise it. A caller
 * that repeats its own intention passes its own key in the request, and that one is left alone.
 */
export class IdempotentTransport implements Transport {
    private readonly next: Transport;
    private readonly keys: IdempotencyKeys;

    public constructor(next: Transport, keys: IdempotencyKeys = new RandomIdempotencyKeys()) {
        this.next = next;
        this.keys = keys;
    }

    public send(request: ApiRequest): Promise<ApiResponse> {
        const needsKey = UNSAFE.includes(request.method) && !(IDEMPOTENCY_KEY_HEADER in request.headers);
        return this.next.send(
            needsKey ? { ...request, headers: { ...request.headers, [IDEMPOTENCY_KEY_HEADER]: this.keys.fresh() } } : request,
        );
    }
}
