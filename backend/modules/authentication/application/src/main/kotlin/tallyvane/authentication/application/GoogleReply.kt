package tallyvane.authentication.application

/**
 * What the browser brought back from Google to the callback: a code with the `state` it answers, or
 * the news that the person said no.
 *
 * The web layer builds one from the query string and asks nothing of it; [ContinueWithGoogleUseCase]
 * is the only reader, and it reads within this module.
 */
public sealed interface GoogleReply {
    /**
     * Google sent a [code] to trade, with the [state] it was given.
     */
    public class Granted(internal val code: String, internal val state: String) : GoogleReply {
        override fun toString(): String = "Granted(***)"
    }

    /**
     * Google sent the person back without a code: they cancelled, or Google refused to ask them.
     */
    public class Declined : GoogleReply {
        override fun equals(other: Any?): Boolean = other is Declined

        override fun hashCode(): Int = Declined::class.hashCode()

        override fun toString(): String = "Declined"
    }
}
