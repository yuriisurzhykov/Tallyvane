import type { CodeKind } from "@/entities/sign-in";

const TOTP_LENGTH = 6;
const RECOVERY_LENGTH = 10;

/**
 * What the person typed into the code field, cleaned up the way the server reads it: the app's code is the
 * digits in it, a recovery code is its letters and digits in capitals, so spaces, dashes and case do not matter.
 */
export class TypedCode {
    private readonly text: string;

    public constructor(text: string) {
        this.text = text;
    }

    public forKind(kind: CodeKind): string {
        return kind === "totp" ? this.text.replace(/\D/g, "") : this.text.replace(/[\s-]/g, "").toUpperCase();
    }

    /** Whether it is as long as a code of that kind. A shorter one is not sent: every wrong code lengthens the pause. */
    public isComplete(kind: CodeKind): boolean {
        return this.forKind(kind).length === (kind === "totp" ? TOTP_LENGTH : RECOVERY_LENGTH);
    }
}
