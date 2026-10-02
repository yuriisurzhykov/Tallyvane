import type { ApiRequest, ApiResponse, Transport } from "./ApiRequest";
import { ProblemError } from "./ProblemError";
import { SingleFlight } from "./SingleFlight";

/** Whoever can get the person signed in again (ADR-084). The transport knows only that this can be asked. */
export interface ExpiredSessionHandler {
    reauthenticate(): Promise<void>;
}

/**
 * Handles a session that ended under a request: waits for the handler, then repeats the request once.
 * Several requests expiring together wait for the same sign-in, so one dialog opens, not five. Repeating
 * is safe even for a `POST`, because the `401` is decided before the request reaches any business logic,
 * and the repeat carries the same `Idempotency-Key` in any case.
 */
export class ReauthenticatingTransport implements Transport {
    private readonly next: Transport;
    private readonly handler: ExpiredSessionHandler;
    private readonly flight = new SingleFlight();

    public constructor(next: Transport, handler: ExpiredSessionHandler) {
        this.next = next;
        this.handler = handler;
    }

    public async send(request: ApiRequest): Promise<ApiResponse> {
        try {
            return await this.next.send(request);
        } catch (failure) {
            if (!(failure instanceof ProblemError) || !failure.isSessionExpired()) {
                throw failure;
            }
            await this.flight.run(() => this.handler.reauthenticate());
            return this.next.send(request);
        }
    }
}
