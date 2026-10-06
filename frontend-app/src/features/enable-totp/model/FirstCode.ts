const LENGTH = 6;

/** The first code typed to turn TOTP on: the digits in what was typed, whatever the grouping. */
export class FirstCode {
    private readonly text: string;

    public constructor(text: string) {
        this.text = text;
    }

    public digits(): string {
        return this.text.replace(/\D/g, "");
    }

    /** Whether there are as many digits as a code has. A shorter one is not sent. */
    public isComplete(): boolean {
        return this.digits().length === LENGTH;
    }
}
