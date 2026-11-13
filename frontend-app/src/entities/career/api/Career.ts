import type { Api, components } from "frontend-shared/api";

export type Home = components["schemas"]["Home"];
export type Resume = components["schemas"]["Resume"];

/** The goal, the one next step, the route and the résumé: all chosen by the server's rules. */
export class Career {
    private readonly api: Api;

    public constructor(api: Api) {
        this.api = api;
    }

    public async home(): Promise<Home> {
        return this.api.get("/home");
    }

    public async resume(): Promise<Resume> {
        return this.api.get("/resume");
    }

    public async choose(role: string): Promise<void> {
        await this.api.put("/goal", { role });
    }
}
