package tallyvane.identity.contract

import tallyvane.platform.kernel.ArchitectureException

/**
 * Who is making this request — resolved once at the HTTP boundary by [PrincipalResolver] and
 * handed to every module downstream as an ordinary parameter, never as a second lookup against
 * `identity`.
 *
 * A closed hierarchy, not one type with a capability set, so a use case that only makes sense for
 * a human — deleting one's own account, say — can require [User] and let the compiler refuse any
 * other principal kind. Why, and why `ServicePrincipal` is named but not yet a case:
 * `contract/README.md`.
 *
 * Authorization — what a principal may do, not who it is — is a separate contract, out of scope here.
 */
public sealed interface Principal {
    /**
     * A human, signed in through one of `identity`'s primary methods: password, Google OAuth, or
     * Google Identity Services.
     */
    @ArchitectureException(
        rule = "contract-no-logic",
        reason = "A sealed-interface case holding one immutable field is not logic; the check's " +
            "nested-name allow-list was never widened to recognise this shape.",
        adr = "ADR-075",
    )
    public data class User(public val id: UserId) : Principal
}
