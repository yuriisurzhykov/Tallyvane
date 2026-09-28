package tallyvane.identity.infrastructure

import tallyvane.identity.application.port.TokenHasher
import tallyvane.identity.contract.PrincipalResolver
import tallyvane.identity.infrastructure.persistence.SessionStoreOverExposed
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunner

/**
 * The one way a composition root outside this module builds a real [PrincipalResolver] —
 * `PrincipalResolverOverSessionStore` itself stays `internal`, per `adapter-is-internal`, so
 * `server` cannot name it directly. Named `Factory` rather than left unnamed: that suffix is
 * exactly what the same rule carves an exception for, since a composition root has to be able to
 * construct *something* from a module it does not otherwise see inside of.
 */
public class PrincipalResolverFactory {
    /**
     * @param transactions The pool-backed [TransactionRunner] `server`'s own platform wiring
     * already built — this factory opens no pool of its own.
     * @param tokenPepper The same pepper [TokenHasher.Hmac] used to mint every access token this
     * resolver will ever look up; a different one here would make every session unresolvable.
     */
    public fun resolver(
        transactions: TransactionRunner,
        clock: Clock,
        tokenPepper: Secret,
        tokenPepperVersion: Int,
    ): PrincipalResolver = PrincipalResolverOverSessionStore(
        sessions = SessionStoreOverExposed(),
        tokenHasher = TokenHasher.Hmac(tokenPepper, tokenPepperVersion),
        clock = clock,
        transactions = transactions,
    )
}
