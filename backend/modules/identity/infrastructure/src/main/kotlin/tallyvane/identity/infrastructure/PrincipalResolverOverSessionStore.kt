package tallyvane.identity.infrastructure

import tallyvane.identity.application.port.SessionStore
import tallyvane.identity.application.port.TokenHasher
import tallyvane.identity.contract.Principal
import tallyvane.identity.contract.PrincipalResolver
import tallyvane.identity.contract.ResolvedPrincipal
import tallyvane.identity.domain.token.TokenValue
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.Verdict
import tallyvane.identity.contract.SessionId as ContractSessionId
import tallyvane.identity.contract.UserId as ContractUserId

/**
 * [PrincipalResolver] over [SessionStore] — the one adapter every other module's request pipeline
 * runs through, via `platform:http`'s `RequestPrincipalResolver` extension point wired in the
 * composition root.
 *
 * A malformed [rawSessionCookie] — one that does not even have [TokenValue]'s own shape — resolves
 * to `null` rather than throwing: an edited, truncated or otherwise tampered cookie is exactly as
 * "no valid session" as an unrecognised one, per [PrincipalResolver.resolve]'s own KDoc, and this
 * is the one call site the raw, untrusted browser-supplied string ever reaches.
 */
internal class PrincipalResolverOverSessionStore(
    private val sessions: SessionStore,
    private val tokenHasher: TokenHasher,
    private val clock: Clock,
    private val transactions: TransactionRunner,
) : PrincipalResolver {
    override suspend fun resolve(rawSessionCookie: String): ResolvedPrincipal? {
        val presented = runCatching { TokenValue(rawSessionCookie) }.getOrNull() ?: return null
        return transactions.inTransaction {
            val session = sessions.findByAccessTokenHash(tokenHasher.hash(presented), clock.now())
            val resolved = session?.let {
                ResolvedPrincipal(
                    principal = Principal.User(ContractUserId(it.userId.value)),
                    sessionId = ContractSessionId(it.id.value),
                )
            }
            Verdict.Commit(resolved)
        }
    }
}
