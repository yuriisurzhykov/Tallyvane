package tallyvane.authentication.application

import tallyvane.authentication.application.port.PolicyVersions
import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.Admins
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict

/**
 * An administrator asks what a kept version would do if it were put in force (ADR-098).
 *
 * The preview is always of a version that is kept: a new version is added first, which changes nothing for
 * anyone, and its consequences are read by its number. That is the same for a new version and for a rollback, and
 * the server keeps no draft. Reading writes nothing.
 */
public interface PreviewPolicyVersionUseCase : UseCase {
    /**
     * @param administrator The signed-in account asking.
     * @param purpose The word of the purpose, such as `login`.
     * @param number The number of the version among the versions of the purpose.
     */
    public suspend fun preview(administrator: AccountId, purpose: String, number: Int): PolicyVersionPreviewed

    public class PreviewPolicyVersion(
        private val admins: Admins,
        private val versions: PolicyVersions,
        private val reach: Reach,
        private val words: PolicyWords,
        private val transactions: TransactionRunner,
    ) : PreviewPolicyVersionUseCase {
        override suspend fun preview(administrator: AccountId, purpose: String, number: Int): PolicyVersionPreviewed =
            transactions.inTransaction {
                val named = words.purposeNamed(purpose)
                val kept = named?.let { versions.history(it).numbered(number) }
                when {
                    !admins.isAdmin(administrator) ->
                        Verdict.Rollback(PolicyVersionPreviewed.Failed.NotAnAdministrator())

                    named == null -> Verdict.Rollback(
                        PolicyVersionPreviewed.Failed.Invalid(listOf(words.unknownPurpose(purpose))),
                    )

                    kept == null -> Verdict.Rollback(PolicyVersionPreviewed.Failed.NoSuchVersion())

                    else -> Verdict.Commit(PolicyVersionPreviewed.Previewed(reach.of(kept, named)))
                }
            }
    }
}
