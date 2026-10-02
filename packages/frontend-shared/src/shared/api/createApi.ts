import { Api } from "./Api";
import { FetchTransport, type FetchTransportOptions } from "./FetchTransport";
import type { IdempotencyKeys } from "./IdempotencyKeys";
import { IdempotentTransport } from "./IdempotentTransport";
import { ProblemTransport } from "./ProblemTransport";
import { ReauthenticatingTransport, type ExpiredSessionHandler } from "./ReauthenticatingTransport";

export interface ApiOptions extends FetchTransportOptions {
    /** Present in a browser, where a session can end under a request. Absent on a server, which never signs anyone in. */
    readonly onExpired?: ExpiredSessionHandler;
    readonly keys?: IdempotencyKeys;
}

/**
 * Builds the chain, outermost first: key, then (optionally) sign in again, then failures as exceptions,
 * then the network. The order is the design: the key must be fixed before a request can be repeated, and
 * a failure must be an exception before anything can decide to repeat it.
 */
export function createApi({ onExpired, keys, ...fetching }: ApiOptions): Api {
    const failing = new ProblemTransport(new FetchTransport(fetching));
    const repeating = onExpired === undefined ? failing : new ReauthenticatingTransport(failing, onExpired);
    return new Api(new IdempotentTransport(repeating, keys));
}
