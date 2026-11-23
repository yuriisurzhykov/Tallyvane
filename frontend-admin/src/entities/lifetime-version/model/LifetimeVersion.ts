import type { components } from "frontend-shared/api";
import type { Diff } from "@/shared/diff";
import { Span } from "@/shared/span";
import { VersionStamp } from "@/shared/history";
import { LifetimeForm, type LifetimeWords } from "./LifetimeForm";

type Wire = components["schemas"]["LifetimeVersion"];

/** One kept version of the session lifetimes of a kind of client, as the server remembers it. */
export class LifetimeVersion {
    private readonly wire: Wire;

    public constructor(wire: Wire) {
        this.wire = wire;
    }

    public number(): number {
        return this.wire.number;
    }

    /** The version every session of its client is judged by now. Exactly one of a history is. */
    public isInForce(): boolean {
        return this.wire.in_force !== undefined;
    }

    public made(): VersionStamp {
        return new VersionStamp(this.wire.made_by, this.wire.made_at, this.wire.comment);
    }

    public inForce(): VersionStamp | undefined {
        const { in_force: now } = this.wire;
        return now === undefined ? undefined : new VersionStamp(now.by, now.since, now.reason);
    }

    public summary(words: LifetimeWords): string {
        return this.asForm().summary(words);
    }

    /** How this version differs from the one in force: what the one in force had that this does not, and what this has that it did not. */
    public changesFrom(inForce: LifetimeVersion, words: LifetimeWords): Diff {
        return this.asForm().changesFrom(inForce.asForm(), words);
    }

    public asForm(): LifetimeForm {
        return new LifetimeForm(
            Span.ofMillis(this.wire.idle_millis),
            Span.ofMillis(this.wire.absolute_millis),
            Span.ofMillis(this.wire.freshness_millis),
        );
    }
}
