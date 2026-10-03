import { describe, expect, it } from "vitest";
import { RecoveryNotice } from "./RecoveryNotice";

class MemoryStorage implements Storage {
    private readonly items = new Map<string, string>();
    public get length(): number { return this.items.size; }
    public clear(): void { this.items.clear(); }
    public key(index: number): string | null { return [...this.items.keys()][index] ?? null; }
    public getItem(key: string): string | null { return this.items.get(key) ?? null; }
    public setItem(key: string, value: string): void { this.items.set(key, value); }
    public removeItem(key: string): void { this.items.delete(key); }
}

const notice = (storage: MemoryStorage | undefined = new MemoryStorage()) => new RecoveryNotice(storage);

describe("RecoveryNotice", () => {
    it("has nothing to show until a recovery code was used", () => {
        expect(notice().pending()).toBeUndefined();
    });

    it("remembers how many codes were left, including none, until it is read", () => {
        const storage = new MemoryStorage();
        notice(storage).remember(0);
        expect(notice(storage).pending()).toBe(0);
        notice(storage).read();
        expect(notice(storage).pending()).toBeUndefined();
    });

    it("ignores a value that is not a count", () => {
        const storage = new MemoryStorage();
        storage.setItem("tallyvane.recovery-notice", "lots");
        expect(notice(storage).pending()).toBeUndefined();
    });

    it("does nothing where the browser has no storage", () => {
        const none = new RecoveryNotice(undefined);
        none.remember(3);
        expect(none.pending()).toBeUndefined();
    });
});
