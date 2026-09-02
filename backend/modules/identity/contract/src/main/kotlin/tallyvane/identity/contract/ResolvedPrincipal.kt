package tallyvane.identity.contract

/**
 * What [PrincipalResolver] hands back for a session cookie that turned out to name a session that
 * is currently valid: who is making the request, and which session vouched for them.
 *
 * Carrying [sessionId] alongside [principal], rather than [principal] alone, is what lets a
 * downstream module hand a session identity straight to `identity`'s own `RevokeSessionUseCase`
 * later without a second resolution step — "sign out of this device" needs to know which session
 * answered the current request, not only who it belongs to.
 */
public data class ResolvedPrincipal(public val principal: Principal, public val sessionId: SessionId)
