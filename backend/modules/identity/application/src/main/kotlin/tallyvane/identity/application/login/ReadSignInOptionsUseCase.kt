package tallyvane.identity.application.login

import tallyvane.identity.application.admin.BootstrapAdminAccountsUseCase
import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

public interface ReadSignInOptionsUseCase : UseCase {
    public suspend fun read(): Set<AuthenticationTokenKind>

    /**
     * Preserves the existing first-read provisioning behavior at the application boundary.
     */
    public class AfterProvisioning(
        private val bootstrap: BootstrapAdminAccountsUseCase,
        private val options: ReadSignInOptionsUseCase,
    ) : ReadSignInOptionsUseCase {
        override suspend fun read(): Set<AuthenticationTokenKind> {
            bootstrap.provisionConfiguredAdmins()
            return options.read()
        }
    }

    public class Read internal constructor(
        private val policies: AuthenticationPolicyStore,
        private val runtimeAvailable: Set<AuthenticationTokenKind>,
        private val transactions: TransactionRunner,
    ) : ReadSignInOptionsUseCase {
        override suspend fun read(): Set<AuthenticationTokenKind> = transactions.inTransaction {
            val enabled = policies.current()?.schemesFor(AuthenticationAction.SIGN_IN)
                ?.flatMapTo(linkedSetOf()) { scheme ->
                    scheme.requiredTokens.filter(AuthenticationTokenKind::isPrimary)
                }
                ?.intersect(runtimeAvailable)
                .orEmpty()
            Verdict.Commit(enabled)
        }
    }
}
