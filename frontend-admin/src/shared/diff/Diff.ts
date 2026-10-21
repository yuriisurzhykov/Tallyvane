import { DiffLine } from "./DiffLine";

/** How one version differs from another, as lines a person reads. Built by the versions themselves; the screen only draws it. */
export class Diff {
    private readonly lines: readonly DiffLine[];

    private constructor(lines: readonly DiffLine[]) {
        this.lines = lines;
    }

    /** The two are the same. */
    public static none(): Diff {
        return new Diff([]);
    }

    /** Lines with no heading over them. */
    public static of(lines: readonly DiffLine[]): Diff {
        return new Diff(lines);
    }

    /** Lines under a heading of their own; with no lines there is no heading either. */
    public static section(title: string, lines: readonly DiffLine[]): Diff {
        return lines.length === 0 ? Diff.none() : new Diff([DiffLine.section(title), ...lines]);
    }

    public and(other: Diff): Diff {
        return new Diff([...this.lines, ...other.lines]);
    }

    public isEmpty(): boolean {
        return this.lines.length === 0;
    }

    public map<T>(show: (line: DiffLine, position: number) => T): T[] {
        return this.lines.map(show);
    }
}
