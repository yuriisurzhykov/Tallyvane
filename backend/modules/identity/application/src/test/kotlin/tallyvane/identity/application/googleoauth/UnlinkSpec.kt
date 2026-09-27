package tallyvane.identity.application.googleoauth

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.AuthenticationActionProofStore
import tallyvane.identity.application.port.AuthenticationPolicyStore
import tallyvane.identity.application.port.CredentialRepositoryFake
import tallyvane.identity.application.port.SessionStoreFake
import tallyvane.identity.application.port.TokenHasherFake
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.application.port.VALID_ACTION_PROOF
import tallyvane.identity.application.secondfactor.AuthenticationActionProofRequirement
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.credential.GoogleSubject
import tallyvane.identity.domain.credential.PasswordHash
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationActionProof
import tallyvane.identity.domain.secondfactor.AuthenticationPolicy
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.session.Session
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.HashedToken
import tallyvane.identity.domain.token.TokenFamilyId
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

class UnlinkSpec :
    StringSpec({
        "requires an action proof and preserves the last policy-usable sign-in method" {
            val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000045"))
            val sessionId = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000046"))
            val now = Instant.parse("2026-01-01T00:00:00Z")
            val users = UserRepositoryFake().also {
                it.insert(
                    User(userId, Email("person@example.test"), null, now, null, true),
                )
            }
            val credentials = CredentialRepositoryFake().also {
                it.save(userId, Credential.GoogleRecord(GoogleSubject("google-subject")))
            }
            val sessions = SessionStoreFake().also {
                it.save(
                    Session(sessionId, userId, DeviceLabel("Browser"), TokenFamilyId(Uuid.random()), now, now, null),
                )
            }
            val policies = object : AuthenticationPolicyStore {
                override suspend fun current() = AuthenticationPolicy.defaults()
                override suspend fun replace(expectedVersion: Long, policy: AuthenticationPolicy) = false
            }
            val proofs = object : AuthenticationActionProofStore {
                override suspend fun save(proof: AuthenticationActionProof) = Unit
                override suspend fun consume(
                    token: HashedToken,
                    userId: UserId,
                    sessionId: SessionId,
                    action: AuthenticationAction,
                    policyVersion: Long,
                    now: Instant,
                ) = true
                override suspend fun deleteAllFor(userId: UserId) = Unit
            }
            val requirement = AuthenticationActionProofRequirement(
                proofs,
                policies,
                sessions,
                TokenHasherFake(),
                ClockFake(now),
            )
            val unlink = UnlinkGoogleAccountUseCase.Unlink(
                users,
                credentials,
                TransactionRunnerFake(),
                requirement,
                policies,
                emptyList(),
                false,
            )

            unlink.unlink(userId, sessionId, VALID_ACTION_PROOF) shouldBe
                UnlinkGoogleAccountUseCase.Result.LastSignInMethod
            credentials.findGoogleFor(userId)?.subject shouldBe GoogleSubject("google-subject")

            credentials.save(userId, Credential.PasswordRecord(PasswordHash(Secret("password"))))
            unlink.unlink(userId, sessionId, null) shouldBe UnlinkGoogleAccountUseCase.Result.InvalidCredential
            credentials.findGoogleFor(userId)?.subject shouldBe GoogleSubject("google-subject")

            unlink.unlink(userId, sessionId, VALID_ACTION_PROOF) shouldBe UnlinkGoogleAccountUseCase.Result.Unlinked
            credentials.findGoogleFor(userId) shouldBe null
        }
    })
