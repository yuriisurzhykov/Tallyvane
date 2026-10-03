import type { ApiRequest, ApiResponse, Transport } from "./ApiRequest";
import { ProblemError } from "./ProblemError";
import { SingleFlight } from "./SingleFlight";

/** Whoever can get the person to prove who they are again (ADR-092). The transport knows only that this can be asked. */
export interface StepUpHandler {
    confirm(): Promise<void>;
}

/**
 * Handles a dangerous act refused for want of a recent proof: waits for the handler, then repeats the
 * request once. Several requests refused together wait for the same confirmation, so one dialog opens, not
 * five. Repeating is safe even for a `DELETE`, because the `403` is decided before the request reaches any
 * business logic, and the repeat carries the same `Idempotency-Key` in any case.
 */
export class ConfirmingTransport implements Transport {
    private readonly next: Transport;
    private readonly handler: StepUpHandler;
    private readonly flight = new SingleFlight();

    public constructor(next: Transport, handler: StepUpHandler) {
        this.next = next;
        this.handler = handler;
    }

    public async send(request: ApiRequest): Promise<ApiResponse> {
        try {
            return await this.next.send(request);
        } catch (failure) {
            if (!(failure instanceof ProblemError) || !failure.isStepUpRequired()) {
                throw failure;
            }
            await this.flight.run(() => this.handler.confirm());
            return this.next.send(request);
        }
    }
}
