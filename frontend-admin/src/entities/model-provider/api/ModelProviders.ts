import type { Api, components } from "frontend-shared/api";

export type ModelsScreen = components["schemas"]["ModelProviders"];
export type ModelProvider = components["schemas"]["ModelProvider"];
export type ModelProviderSent = components["schemas"]["ModelProviderSent"];

/** The model providers, the one in use, and what calls to them cost. Every change answers with the whole screen. */
export class ModelProviders {
    private readonly api: Api;

    public constructor(api: Api) {
        this.api = api;
    }

    public async screen(): Promise<ModelsScreen> {
        return this.api.get("/model-providers");
    }

    public async save(id: string, sent: ModelProviderSent): Promise<ModelsScreen> {
        return this.api.put("/model-provider/{id}", sent, { params: { id } });
    }

    public async choose(id: string): Promise<ModelsScreen> {
        return this.api.put("/active-model-provider", { id });
    }

    public async remove(id: string): Promise<ModelsScreen> {
        return this.api.delete("/model-provider-removal/{id}", { params: { id } });
    }
}
