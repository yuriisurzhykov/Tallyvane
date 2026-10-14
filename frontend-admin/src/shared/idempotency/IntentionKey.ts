import { ProblemError } from "frontend-shared/api";

/**
 * The `Idempotency-Key` of one intention (ADR-086): generated once and sent again, unchanged, while the person
 * repeats the same request because its answer never arrived. A request that differs is another intention and
 * gets a key of its own; so does the request after one that was carried out or turned down.
 */
export class IntentionKey {
    private readonly fresh: () => string;
    private open: { readonly request: string; readonly key: string } | undefined;

    public constructor(fresh: () => string = () => crypto.randomUUID()) {
        this.fresh = fresh;
    }

    /** Sends the request with the key of its intention and ends the intention if the server gave a final answer. */
    public async send<Answer>(request: unknown, send: (key: string) => Promise<Answer>): Promise<Answer> {
        const key = this.keyFor(JSON.stringify(request));
        try {
            const answer = await send(key);
            this.open = undefined;
            return answer;
        } catch (failure) {
            if (failure instanceof ProblemError && failure.wasRefused()) {
                this.open = undefined;
            }
            throw failure;
        }
    }

    private keyFor(request: string): string {
        if (this.open?.request === request) {
            return this.open.key;
        }
        this.open = { request, key: this.fresh() };
        return this.open.key;
    }
}
