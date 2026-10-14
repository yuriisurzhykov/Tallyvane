package tallyvane.authentication.application

import tallyvane.authentication.application.port.PolicyVersions
import tallyvane.authentication.domain.PolicyVersion
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.SignInPolicy
import tallyvane.identity.contract.AccountId
import kotlin.time.Instant

/**
 * [PolicyVersions] that keeps them in lists, for tests of the code that uses the port (ADR-044).
 *
 * [PolicyVersionsFakeSpec] holds it to the same suite as the adapter over Postgres.
 */
class PolicyVersionsFake : PolicyVersions {
    private val versions = mutableMapOf<Purpose, MutableList<Made>>()
    private val activations = mutableMapOf<Purpose, MutableList<Activated>>()

    override fun active(purpose: Purpose): PolicyVersion? = activations[purpose]?.lastOrNull()?.version

    override fun history(purpose: Purpose): KeptVersions = KeptVersions(
        versions[purpose].orEmpty().reversed().map { KeptVersions.Entry(it.version, it.madeBy, it.comment, it.at) },
        activations[purpose]?.lastOrNull()?.let {
            KeptVersions.InForce(NumberOf(it.version).number(), it.by, it.reason, it.at)
        },
    )

    override fun add(policy: SignInPolicy, change: Change, at: Instant): PolicyVersion {
        val purpose = PurposeOf(policy).purpose()
        val kept = versions.getOrPut(purpose) { mutableListOf() }
        val story = ChangeStory(change)
        return PolicyVersion(kept.size + 1, policy).also { kept += Made(it, story.author(), story.comment(), at) }
    }

    override fun activate(version: PolicyVersion, change: Change, at: Instant) {
        val purpose = PurposeOf(version).purpose()
        check(versions[purpose].orEmpty().any { VersionStory(it.version) == VersionStory(version) }) {
            "No version kept by this fake says what the version given says, so it cannot be activated."
        }
        val story = ChangeStory(change)
        activations.getOrPut(purpose) { mutableListOf() } += Activated(version, story.author(), story.comment(), at)
    }

    override fun toString(): String = "PolicyVersionsFake(versions=${versions.values.sumOf { it.size }})"

    private class Made(val version: PolicyVersion, val madeBy: AccountId, val comment: String, val at: Instant)

    private class Activated(val version: PolicyVersion, val by: AccountId, val reason: String, val at: Instant)
}
