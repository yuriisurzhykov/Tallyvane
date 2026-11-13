import type { Api, components } from "frontend-shared/api";

type WireScreen = components["schemas"]["ModelProviders"];
type WireProvider = components["schemas"]["ModelProvider"];
type WireSent = components["schemas"]["ModelProviderSent"];
type WireCost = components["schemas"]["ModelCost"];

export type ModelProviderSent = Omit<WireSent, "base_url" | "price_in" | "price_out"> & {
    baseUrl?: string | null;
    priceIn: number;
    priceOut: number;
};
export type ModelProvider = Omit<WireProvider, "base_url" | "key_hint" | "price_in" | "price_out"> & {
    baseUrl?: string | null;
    keyHint: string;
    priceIn: number;
    priceOut: number;
};
type ModelCost = Omit<WireCost, "tokens_in" | "tokens_out"> & { tokensIn: number; tokensOut: number };
export interface ModelsScreen {
    providers: ModelProvider[];
    costs: ModelCost[];
}

/** The model providers, the one in use, and what calls to them cost. Every change answers with the whole screen. */
export class ModelProviders {
    private readonly api: Api;

    public constructor(api: Api) {
        this.api = api;
    }

    public async screen(): Promise<ModelsScreen> {
        return ModelProviders.shown(await this.api.get("/model-providers"));
    }

    public async save(id: string, sent: ModelProviderSent): Promise<ModelsScreen> {
        const { baseUrl, priceIn, priceOut, ...rest } = sent;
        return ModelProviders.shown(await this.api.put("/model-provider/{id}", {
            ...rest, base_url: baseUrl ?? null, price_in: priceIn, price_out: priceOut,
        }, { params: { id } }));
    }

    public async choose(id: string): Promise<ModelsScreen> {
        return ModelProviders.shown(await this.api.put("/active-model-provider", { id }));
    }

    public async remove(id: string): Promise<ModelsScreen> {
        return ModelProviders.shown(await this.api.delete("/model-provider-removal/{id}", { params: { id } }));
    }

    /** The API speaks snake_case; the form and the screen keep their own field names. */
    private static shown(screen: WireScreen): ModelsScreen {
        return {
            providers: screen.providers.map(({ base_url, key_hint, price_in, price_out, ...rest }) => ({
                ...rest, ...(base_url !== undefined ? { baseUrl: base_url } : {}), keyHint: key_hint, priceIn: price_in, priceOut: price_out,
            })),
            costs: screen.costs.map(({ tokens_in, tokens_out, ...rest }) => ({ ...rest, tokensIn: tokens_in, tokensOut: tokens_out })),
        };
    }
}
