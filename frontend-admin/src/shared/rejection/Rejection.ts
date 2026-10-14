import { ProblemError, type Fault } from "frontend-shared/api";

/**
 * The server refusing a request because of what it says (`422`): every fault at once, so the screen can word all
 * of them next to the form instead of finding them one save at a time. A failure that is not about fields is
 * not a rejection; `from` says `undefined` for it and the caller treats it as a failure.
 */
export class Rejection {
    private readonly faults: readonly Fault[];
    private readonly detail: string | undefined;

    private constructor(faults: readonly Fault[], detail: string | undefined) {
        this.faults = faults;
        this.detail = detail;
    }

    public static from(failure: unknown): Rejection | undefined {
        if (failure instanceof ProblemError && failure.hasStatus(422)) {
            return new Rejection(failure.faults(), failure.detail());
        }
        return undefined;
    }

    public isEmpty(): boolean {
        return this.faults.length === 0;
    }

    public has(field: string, code?: string): boolean {
        return this.faults.some((fault) => fault.field === field && (code === undefined || fault.code === code));
    }

    /** What is wrong with these fields only, for the place next to the field. */
    public about(...fields: readonly string[]): Rejection {
        return new Rejection(this.faults.filter((fault) => fields.includes(fault.field)), this.detail);
    }

    /** What is wrong with everything but these fields, for the list that names what no field of its own shows. */
    public except(...fields: readonly string[]): Rejection {
        return new Rejection(this.faults.filter((fault) => !fields.includes(fault.field)), this.detail);
    }

    /**
     * One sentence for each fault, in the order the server listed them. With `whenUnnamed`, a rejection that names
     * no fault at all is worded from the server's own detail instead of being silent.
     */
    public describe(phrase: (fault: Fault) => string, whenUnnamed?: (detail: string | undefined) => string): string[] {
        if (this.faults.length === 0) {
            return whenUnnamed === undefined ? [] : [whenUnnamed(this.detail)];
        }
        return this.faults.map(phrase);
    }
}
