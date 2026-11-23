import { describe, expect, it } from "vitest";
import { Api } from "frontend-shared/api";
import { ModelProviders } from "./ModelProviders";

type Transport = ConstructorParameters<typeof Api>[0];
type ApiRequest = Parameters<Transport["send"]>[0];
const ID = "00000000-0000-7000-8000-000000000001";

const wire = {
    providers: [{
        id: ID, name: "Example", kind: "openai_compatible",
        base_url: "https://example.com/v1", key_hint: "…test", fast: "fast", careful: "careful",
        price_in: 1, price_out: 2, active: false,
    }],
    costs: [{ provider: "Example", model: "fast", spec: "story", calls: 1, remembered: 0, tokens_in: 10, tokens_out: 20, dollars: 0.1 }],
};

function client() {
    const requests: ApiRequest[] = [];
    const transport: Transport = {
        send(request) {
            requests.push(request);
            return Promise.resolve({ status: 200, body: wire, headers: new Headers() });
        },
    };
    return { providers: new ModelProviders(new Api(transport)), requests };
}

describe("ModelProviders wire format", () => {
    it("sends the snake_case fields the server deserializes", async () => {
        const { providers, requests } = client();
        await providers.save(ID, {
            name: "Example", kind: "openai_compatible", baseUrl: "https://example.com/v1", key: "test-key",
            fast: "fast", careful: "careful", priceIn: 1, priceOut: 2,
        });
        expect(requests[0]?.body).toEqual({
            name: "Example", kind: "openai_compatible", base_url: "https://example.com/v1", key: "test-key",
            fast: "fast", careful: "careful", price_in: 1, price_out: 2,
        });
    });

    it("reads the server's provider and cost fields for every screen response", async () => {
        const { providers } = client();
        const screens = [await providers.screen(), await providers.choose(ID), await providers.remove(ID)];
        for (const screen of screens) {
            expect(screen.providers[0]).toMatchObject({ baseUrl: "https://example.com/v1", keyHint: "…test", priceIn: 1, priceOut: 2 });
            expect(screen.costs[0]).toMatchObject({ tokensIn: 10, tokensOut: 20 });
        }
    });
});
