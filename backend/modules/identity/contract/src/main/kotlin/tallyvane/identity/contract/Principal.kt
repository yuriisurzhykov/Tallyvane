package tallyvane.identity.contract

import tallyvane.platform.kernel.ArchitectureException

/**
 * Who is making this request — resolved once at the HTTP boundary by [PrincipalResolver] and
 * handed to every module downstream as an ordinary parameter, never as a second lookup against
 * `identity`.
 *
 * A closed hierarchy rather than one type with a capability set or a discriminator field, so a use
 * case that only ever makes sense for a human — deleting one's own account, say — can require
 * [User] in its own signature and let the compiler refuse a caller holding some other kind of
 * principal, once one exists. That is the same reasoning `backend/.plans/backend-access-and-api.md`
 * already recorded for this exact question: "a closed hierarchy of four cases instead of one type
 * with a set of permissions, so a scenario that needs an owner does not accept an extension's token
 * at the compiler level".
 *
 * `ServicePrincipal` — the machine-to-machine half of this hierarchy, for a second process calling
 * this one directly — is named and reserved rather than added as a case now. There is no second
 * service in this system to authenticate yet, and a case with no real value able to construct it
 * would be a promise this pass cannot keep; see `identity/README.md`.
 *
 * What authorization does with a [Principal] — what it may do, not merely who it is — is out of
 * scope for this pass by design. `identity` publishes identity; a separate contract, designed in
 * its own session, will publish permission.
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
