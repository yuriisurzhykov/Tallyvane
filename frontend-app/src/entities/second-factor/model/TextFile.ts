/** Hands a piece of text to the browser as a file the person keeps. Nothing is stored here. */
export class TextFile {
    private readonly name: string;
    private readonly text: string;

    public constructor(name: string, text: string) {
        this.name = name;
        this.text = text;
    }

    public save(): void {
        const address = URL.createObjectURL(new Blob([this.text], { type: "text/plain" }));
        const link = document.createElement("a");
        link.href = address;
        link.download = this.name;
        link.click();
        URL.revokeObjectURL(address);
    }
}
