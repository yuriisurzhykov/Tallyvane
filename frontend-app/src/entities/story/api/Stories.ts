import type { Api, components } from "frontend-shared/api";

export type Story = components["schemas"]["Story"];
export type Fact = components["schemas"]["Fact"];

/** The stories a person told, and the one way to add to them: their own words. */
export class Stories {
    private readonly api: Api;

    public constructor(api: Api) {
        this.api = api;
    }

    public async list(): Promise<Story[]> {
        const { stories } = await this.api.get("/stories");
        return stories;
    }

    /** Words about a new story, or more about `story`. The server keeps only facts that quote them. */
    public async tell(words: string, story?: string): Promise<Story> {
        return this.api.post("/words", story === undefined ? { words } : { words, story });
    }

    public async correct(story: string, fact: string, words: string): Promise<Story> {
        return this.api.post("/corrections", { story, fact, words });
    }
}
