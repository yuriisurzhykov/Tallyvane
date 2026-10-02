package tallyvane.authentication.application

import tallyvane.authentication.application.port.PolicyVersions
import tallyvane.authentication.domain.PolicyVersion
import tallyvane.authentication.domain.Purpose
import tallyvane.authentication.domain.SignInPolicy
import kotlin.time.Instant

/**
 * [PolicyVersions] that keeps them in lists, for tests of the code that uses the port (ADR-044).
 *
 * [PolicyVersionsFakeSpec] holds it to the same suite as the adapter over Postgres.
 */
class PolicyVersionsFake : PolicyVersions {
    private val versions = mutableMapOf<Purpose, MutableList<PolicyVersion>>()
    private val activations = mutableMapOf<Purpose, MutableList<PolicyVersion>>()

    override fun active(purpose: Purpose): PolicyVersion? = activations[purpose]?.lastOrNull()

    override fun add(policy: SignInPolicy, at: Instant): PolicyVersion {
        val purpose = PurposeOf(policy).purpose()
        val kept = versions.getOrPut(purpose) { mutableListOf() }
        return PolicyVersion(kept.size + 1, policy).also { kept += it }
    }

    override fun activate(version: PolicyVersion, at: Instant) {
        val purpose = PurposeOf(version).purpose()
        check(versions[purpose].orEmpty().any { VersionStory(it) == VersionStory(version) }) {
            "No version kept by this fake says what the version given says, so it cannot be activated."
        }
        activations.getOrPut(purpose) { mutableListOf() } += version
    }

    override fun toString(): String = "PolicyVersionsFake(versions=${versions.values.sumOf { it.size }})"
}
