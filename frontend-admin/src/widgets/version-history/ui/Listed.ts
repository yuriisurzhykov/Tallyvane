import type { VersionStamp } from "@/shared/history";

/** What the list asks of any kept version, whatever it holds: a policy or a set of lifetimes. */
export interface Listed {
    number(): number;
    isInForce(): boolean;
    made(): VersionStamp;
    inForce(): VersionStamp | undefined;
}
