export interface ViewerProps {
    readonly id: string;
    readonly name: string;
}

/** The signed-in person, as the console knows them. */
export class Viewer {
    private readonly id: string;
    private readonly name: string;

    public constructor(id: string, name: string) {
        this.id = id;
        this.name = name;
    }

    public label(): string {
        return this.name;
    }

    /** For the one place a viewer must cross from a server component to a client one, where only plain values can go. */
    public toProps(): ViewerProps {
        return { id: this.id, name: this.name };
    }

    public is(id: string): boolean {
        return this.id === id;
    }
}
