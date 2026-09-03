package tallyvane.identity.web

import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId

/**
 * [tallyvane.identity.contract.ResolvedPrincipal], translated from `identity:contract`'s own
 * `UserId`/`SessionId` into the `identity:domain` ones every use case actually takes — the two
 * are deliberately separate types across that layer boundary, per `contract/README.md`, so this
 * is the one conversion every authenticated route needs and none of them should repeat by hand.
 */
internal data class ResolvedIdentity(val userId: UserId, val sessionId: SessionId)
