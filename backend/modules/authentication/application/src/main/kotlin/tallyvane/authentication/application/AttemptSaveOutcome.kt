package tallyvane.authentication.application

/**
 * What [tallyvane.authentication.application.port.Attempts.save] did.
 */
public enum class AttemptSaveOutcome {
    /**
     * The attempt is kept as given.
     */
    Saved,

    /**
     * What is kept holds something the given attempt does not, or says something else: another
     * request got there first. Nothing was changed. Load the attempt again and apply the change to
     * what is there.
     */
    Superseded,
}
