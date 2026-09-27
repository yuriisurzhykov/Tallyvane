package tallyvane.identity.application.secondfactor

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.port.AuthenticationActionProofStore
import tallyvane.identity.application.port.AuthenticationPolicyStoreFake
import tallyvane.identity.application.port.CredentialRepositoryFake
import tallyvane.identity.application.port.PasswordHasherFake
import tallyvane.identity.application.port.SessionStoreFake
import tallyvane.identity.application.port.TokenFactoryFake
import tallyvane.identity.application.port.TokenHasherFake
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.secondfactor.AuthenticationAction
import tallyvane.identity.domain.secondfactor.AuthenticationActionProof
import tallyvane.identity.domain.secondfactor.AuthenticationTokenKind
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

class AuthorizeSpec :
    StringSpec({
        "grants and persists an action proof only after the strongest available password scheme verifies" {
            val fixture = AuthorizeFixture()
            fixture.addPasswordUser()

            val schemes = fixture.useCase.read(
                fixture.userId,
                fixture.sessionId,
                AuthenticationAction.CHANGE_PRIMARY_CREDENTIAL,
            )
            schemes.map { it.id } shouldBe listOf("change-primary-password-primary")

            val result = fixture.useCase.authorize(fixture.request("correct-password"))

            val authorized = result as? AuthorizeAuthenticationActionUseCase.Result.Authorized
                ?: error("Expected a policy-authorized action proof")
            authorized.schemeId shouldBe "change-primary-password-primary"
            authorized.assuranceRank shouldBe 1
            fixture.savedProofs.single().let { proof ->
                proof.userId shouldBe fixture.userId
                proof.sessionId shouldBe fixture.sessionId
                proof.action shouldBe AuthenticationAction.CHANGE_PRIMARY_CREDENTIAL
                proof.policyVersion shouldBe 1
                proof.schemeId shouldBe "change-primary-password-primary"
                proof.assuranceRank shouldBe 1
            }
        }

        "rejects a wrong password and creates no proof" {
            val fixture = AuthorizeFixture()
            fixture.addPasswordUser()

            fixture.useCase.authorize(fixture.request("wrong-password")) shouldBe
                AuthorizeAuthenticationActionUseCase.Result.Refused
            fixture.savedProofs shouldBe emptyList()
        }

        "rejects proof requests from a session that belongs to another account" {
            val fixture = AuthorizeFixture()
            fixture.addPasswordUser()
            val request = fixture.request("correct-password").copy(
                userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000999")),
            )

            fixture.useCase.authorize(request) shouldBe AuthorizeAuthenticationActionUseCase.Result.Refused
            fixture.savedProofs shouldBe emptyList()
        }
    })

private class AuthorizeFixture {
    val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000211"))
    val sessionId = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000212"))
    val credentials = CredentialRepositoryFake()
    val sessions = SessionStoreFake()
    val savedProofs = mutableListOf<AuthenticationActionProof>()
    val proofs = object : AuthenticationActionProofStore {
        override suspend fun save(proof: AuthenticationActionProof) {
            savedProofs += proof
        }

        override suspend fun consume(
            token: HashedToken,
            userId: UserId,
            sessionId: SessionId,
            action: AuthenticationAction,
            policyVersion: Long,
            now: Instant,
        ): Boolean = false

        override suspend fun deleteAllFor(userId: UserId) = Unit
    }
    private val users = UserRepositoryFake()
    private val passwords = PasswordHasherFake()
    private val now = Instant.parse("2026-01-01T00:00:00Z")
    val useCase = AuthorizeAuthenticationActionUseCase.Authorize(
        users,
        credentials,
        passwords,
        null,
        null,
        SecondFactorMethodRegistry.Default(emptyList()),
        AuthenticationPolicyStoreFake(),
        proofs,
        sessions,
        TokenFactoryFake(),
        TokenHasherFake(),
        ClockFake(now),
        TransactionRunnerFake(),
    )

    init {
        sessions.saved[sessionId] = Session(
            sessionId,
            userId,
            DeviceLabel("Browser"),
            TokenFamilyId(Uuid.parse("00000000-0000-7000-8000-000000000213")),
            now,
            now,
            null,
        )
    }

    suspend fun addPasswordUser() {
        users.insert(User(userId, Email("person@example.test"), null, now, null, emailVerified = true))
        credentials.save(userId, Credential.PasswordRecord(passwords.hash(Secret("correct-password"))))
    }

    fun request(password: String) = AuthorizeAuthenticationActionUseCase.Request(
        userId,
        sessionId,
        AuthenticationAction.CHANGE_PRIMARY_CREDENTIAL,
        listOf(AuthorizeAuthenticationActionUseCase.PresentedToken(AuthenticationTokenKind.PASSWORD, password)),
    )
}
