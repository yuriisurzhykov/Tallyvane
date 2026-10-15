export type DiffKind = "section" | "removed" | "added";

/** How a line is drawn, kind by kind: each kind is given its text and answers with whatever the caller draws with. */
export type DiffLook<Appearance> = Readonly<Record<DiffKind, (text: string) => Appearance>>;

/** One line of a list of differences: a heading of a part, something that was there and is not, or something that is now and was not. */
export class DiffLine {
    private readonly kind: DiffKind;
    private readonly text: string;

    private constructor(kind: DiffKind, text: string) {
        this.kind = kind;
        this.text = text;
    }

    public static section(text: string): DiffLine {
        return new DiffLine("section", text);
    }

    public static removed(text: string): DiffLine {
        return new DiffLine("removed", text);
    }

    public static added(text: string): DiffLine {
        return new DiffLine("added", text);
    }

    public appearAs<Appearance>(look: DiffLook<Appearance>): Appearance {
        return look[this.kind](this.text);
    }
}
