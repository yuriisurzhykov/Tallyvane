import type { PolicyVersion } from "./PolicyVersion";

/** The versions kept for one purpose, newest first. */
export class PolicyHistory {
    private readonly versions: readonly PolicyVersion[];

    public constructor(versions: readonly PolicyVersion[]) {
        this.versions = versions;
    }

    public isEmpty(): boolean {
        return this.versions.length === 0;
    }

    public map<T>(show: (version: PolicyVersion) => T): T[] {
        return this.versions.map(show);
    }

    /** The version in force; every history has one, but a history not yet read has none. */
    public inForce(): PolicyVersion | undefined {
        return this.versions.find((version) => version.isInForce());
    }

    /** The version a new one is started from when no particular one was picked: the one in force, else the newest. */
    public startingPoint(): PolicyVersion | undefined {
        return this.inForce() ?? this.versions[0];
    }
}
