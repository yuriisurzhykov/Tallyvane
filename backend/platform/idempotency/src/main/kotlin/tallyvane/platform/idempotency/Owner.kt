package tallyvane.platform.idempotency

import kotlin.uuid.Uuid

/**
 * Whose key it is. Two owners can send the same key and neither can see the other's claim, so a key
 * that leaks into a log or a screenshot returns nothing to whoever finds it.
 *
 * The owner is the subject that authenticating the request established: the person, whether they
 * came by cookie or by a device token. It is deliberately not the session or the token. A token that
 * was refreshed between a request and its repeat would turn the repeat into a stranger, and that
 * repeat is the case this exists for (ADR-086).
 *
 * A request nobody authenticated is [anonymous]. Every request is, until sessions exist.
 */
@JvmInline
public value class Owner private constructor(private val text: String) {
    /**
     * Said to the claim that holds it, and to nobody else: storage reads it through
     * [Claim.writeTo].
     */
    internal fun text(): String = text

    public companion object {
        /**
         * Nobody is known. Claims of anonymous requests share one namespace, which is acceptable
         * because they are the sign-in steps, whose answers carry credentials and are never stored.
         */
        public fun anonymous(): Owner = Owner("anonymous")

        /**
         * The person [id] names, however they authenticated.
         */
        public fun subject(id: Uuid): Owner = Owner("subject:$id")
    }
}
