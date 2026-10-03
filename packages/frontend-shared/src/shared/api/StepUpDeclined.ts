/** What waiting requests fail with when the person chose not to give the proof: a decision, not a breakage. */
export class StepUpDeclined extends Error {
    public constructor() {
        super("The person did not confirm.");
        this.name = "StepUpDeclined";
    }
}
