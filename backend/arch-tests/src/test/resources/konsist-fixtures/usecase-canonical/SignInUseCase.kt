package tallyvane.identity.application

import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

interface SignInUseCase : UseCase {
    suspend fun signIn(request: SignInRequest): SignInOutcome

    class SignIn(
        private val users: Users,
        private val sessions: Sessions,
        private val transactions: TransactionRunner,
        private val clock: Clock,
    ) : SignInUseCase {
        override suspend fun signIn(request: SignInRequest): SignInOutcome {
            val decision = SignInPolicy.decide(request, clock.now())
            if (decision is Denied) {
                return SignInOutcome.Rejected(decision.reason)
            }
            val session =
                transactions.inTransaction {
                    users.upsert(decision.user)
                    Verdict.Commit(sessions.open(decision.user.id))
                }
            return SignInOutcome.Succeeded(session)
        }
    }
}
