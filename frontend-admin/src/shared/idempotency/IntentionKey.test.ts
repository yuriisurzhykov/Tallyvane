import { describe, expect, it } from "vitest";
import { ProblemError } from "frontend-shared/api";
import { IntentionKey } from "./IntentionKey";

const answered = (status: number) =>
    ProblemError.from({
        status,
        headers: new Headers(),
        body: { type: "https://tallyvane.com/errors/validation-failed", title: "No", status },
    });

class Server {
    public readonly keys: string[] = [];
    private readonly outcomes: (() => unknown)[];

    public constructor(...outcomes: (() => unknown)[]) {
        this.outcomes = outcomes;
    }

    public send = (key: string): Promise<string> => {
        this.keys.push(key);
        const outcome = this.outcomes.shift();
        return outcome === undefined ? Promise.resolve("ok") : Promise.resolve().then(() => outcome() as string);
    };
}

const lost = () => { throw new Error("offline"); };
const refuse = (status: number) => () => { throw answered(status); };

const counting = () => {
    let count = 0;
    return () => `key-${String(++count)}`;
};

describe("IntentionKey", () => {
    it("sends the same key again while the same request is repeated after a lost answer", async () => {
        const intention = new IntentionKey(counting());
        const server = new Server(lost, lost);

        await expect(intention.send({ a: 1 }, server.send)).rejects.toThrow("offline");
        await expect(intention.send({ a: 1 }, server.send)).rejects.toThrow("offline");
        await intention.send({ a: 1 }, server.send);

        expect(server.keys).toEqual(["key-1", "key-1", "key-1"]);
    });

    it("gives the next intention a key of its own once the first was carried out", async () => {
        const intention = new IntentionKey(counting());
        const server = new Server();

        await intention.send({ a: 1 }, server.send);
        await intention.send({ a: 1 }, server.send);

        expect(server.keys).toEqual(["key-1", "key-2"]);
    });

    it("gives a changed request a key of its own", async () => {
        const intention = new IntentionKey(counting());
        const server = new Server(lost);

        await expect(intention.send({ a: 1 }, server.send)).rejects.toThrow();
        await intention.send({ a: 2 }, server.send);

        expect(server.keys).toEqual(["key-1", "key-2"]);
    });

    it("ends the intention when the server turned the request down", async () => {
        const intention = new IntentionKey(counting());
        const server = new Server(refuse(422));

        await expect(intention.send({ a: 1 }, server.send)).rejects.toBeInstanceOf(ProblemError);
        await intention.send({ a: 1 }, server.send);

        expect(server.keys).toEqual(["key-1", "key-2"]);
    });

    it.each([500, 409, 429])("keeps the intention open after a %i, which may have carried the work out", async (status) => {
        const intention = new IntentionKey(counting());
        const server = new Server(refuse(status));

        await expect(intention.send({ a: 1 }, server.send)).rejects.toBeInstanceOf(ProblemError);
        await intention.send({ a: 1 }, server.send);

        expect(server.keys).toEqual(["key-1", "key-1"]);
    });
});
