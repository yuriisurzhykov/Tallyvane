/** The ten recovery codes of a set-up, told once. Each works once. */
export class RecoveryCodes {
    private readonly codes: readonly string[];

    public constructor(codes: readonly string[]) {
        this.codes = codes;
    }

    public each<T>(show: (code: string, position: number) => T): T[] {
        return this.codes.map((code, position) => show(code, position));
    }

    /** One code to a line, for copying and for a file to keep. */
    public asText(): string {
        return `${this.codes.join("\n")}\n`;
    }
}
