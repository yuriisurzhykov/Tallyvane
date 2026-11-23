import type { LifetimeVersion } from "./LifetimeVersion";

/** The versions kept for one kind of client, newest first. */
export class LifetimeHistory {
    private readonly versions: readonly LifetimeVersion[];

    public constructor(versions: readonly LifetimeVersion[]) {
        this.versions = versions;
    }

    public isEmpty(): boolean {
        return this.versions.length === 0;
    }

    public map<T>(show: (version: LifetimeVersion) => T): T[] {
        return this.versions.map(show);
    }

    /** The version in force; every history has one, but a history not yet read has none. */
    public inForce(): LifetimeVersion | undefined {
        return this.versions.find((version) => version.isInForce());
    }

    /** The version a new one is started from when no particular one was picked: the one in force, else the newest. */
    public startingPoint(): LifetimeVersion | undefined {
        return this.inForce() ?? this.versions[0];
    }
}
