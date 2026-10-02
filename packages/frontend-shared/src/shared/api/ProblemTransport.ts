import type { ApiRequest, ApiResponse, Transport } from "./ApiRequest";
import { ProblemError } from "./ProblemError";

const FIRST_FAILURE = 400;

/** Turns an answer that is a failure into a thrown `ProblemError`, so everything above it deals only in successes. */
export class ProblemTransport implements Transport {
    private readonly next: Transport;

    public constructor(next: Transport) {
        this.next = next;
    }

    public async send(request: ApiRequest): Promise<ApiResponse> {
        const response = await this.next.send(request);
        if (response.status >= FIRST_FAILURE) {
            throw ProblemError.from(response);
        }
        return response;
    }
}
