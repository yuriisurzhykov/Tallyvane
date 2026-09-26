package tallyvane.identity.application.password

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.google.GoogleIdentity
import tallyvane.identity.application.port.CredentialRepositoryFake
import tallyvane.identity.application.port.GoogleOAuthGateway
import tallyvane.identity.application.port.PasswordHasher
import tallyvane.identity.application.port.SessionStoreFake
import tallyvane.identity.application.port.UserRepositoryFake
import tallyvane.identity.domain.credential.Credential
import tallyvane.identity.domain.credential.GoogleSubject
import tallyvane.identity.domain.credential.PasswordHash
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.session.Session
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.token.TokenFamilyId
import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.ClockFake
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.TransactionRunnerFake
import kotlin.time.Instant
import kotlin.uuid.Uuid

class ReauthenticateSpec :
    StringSpec({
        "a valid password proof is stamped on the active session" {
            val fixture = Fixture()
            fixture.addUser()
            fixture.credentials.save(fixture.userId, Credential.PasswordRecord(PasswordHash(Secret("correct"))))

            fixture.reauthenticate.password(fixture.userId, fixture.sessionId, Secret("correct")) shouldBe
                ReauthenticateUseCase.Outcome.REAUTHENTICATED
            fixture.sessions.find(fixture.sessionId)?.reauthenticatedAt shouldBe Fixture.now
        }

        "an invalid password cannot create recent reauthentication" {
            val fixture = Fixture()
            fixture.addUser()
            fixture.credentials.save(fixture.userId, Credential.PasswordRecord(PasswordHash(Secret("correct"))))

            fixture.reauthenticate.password(fixture.userId, fixture.sessionId, Secret("wrong")) shouldBe
                ReauthenticateUseCase.Outcome.INVALID_CREDENTIAL
            fixture.sessions.find(fixture.sessionId)?.reauthenticatedAt shouldBe null
        }

        "Google proof is accepted only when its stable subject is already linked to the current user" {
            val fixture = Fixture()
            fixture.addUser()
            val linkedSubject = GoogleSubject("linked-subject")
            fixture.credentials.save(fixture.userId, Credential.GoogleRecord(linkedSubject))
            fixture.google.identity = GoogleIdentity(linkedSubject, Email("different@example.test"))

            fixture.reauthenticate.google(
                fixture.userId,
                fixture.sessionId,
                "oauth-code",
                "verifier",
                "https://app.example.test/api/v1/auth/google/callback",
            ) shouldBe ReauthenticateUseCase.Outcome.REAUTHENTICATED
            fixture.sessions.find(fixture.sessionId)?.reauthenticatedAt shouldBe Fixture.now
        }

        "a different Google subject cannot create recent reauthentication even when its email matches" {
            val fixture = Fixture()
            fixture.addUser()
            fixture.credentials.save(
                fixture.userId,
                Credential.GoogleRecord(GoogleSubject("linked-subject")),
            )
            fixture.google.identity = GoogleIdentity(GoogleSubject("other-subject"), Email("person@example.test"))

            fixture.reauthenticate.google(
                fixture.userId,
                fixture.sessionId,
                "oauth-code",
                "verifier",
                "https://app.example.test/api/v1/auth/google/callback",
            ) shouldBe ReauthenticateUseCase.Outcome.INVALID_CREDENTIAL
            fixture.sessions.find(fixture.sessionId)?.reauthenticatedAt shouldBe null
        }
    })

private class Fixture {
    val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000111"))
    val sessionId = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000112"))
    val credentials = CredentialRepositoryFake()
    val sessions = SessionStoreFake()
    val google = GoogleGatewayFake()
    private val users = UserRepositoryFake()
    val reauthenticate = ReauthenticateUseCase.Reauthenticate(
        users = users,
        credentials = credentials,
        passwords = object : PasswordHasher {
            override fun hash(raw: Secret) = PasswordHash(raw)
            override fun verify(raw: Secret, hash: PasswordHash) = raw == hash.encoded
        },
        google = google,
        sessions = sessions,
        clock = ClockFake(now),
        transactions = TransactionRunnerFake(),
    )

    init {
        sessions.saved[sessionId] = Session(
            sessionId,
            userId,
            DeviceLabel("Browser"),
            TokenFamilyId(Uuid.parse("00000000-0000-7000-8000-000000000113")),
            now,
            now,
            null,
        )
    }

    suspend fun addUser() {
        users.insert(User(userId, Email("person@example.test"), null, now, null, emailVerified = true))
    }

    companion object {
        val now: Instant = Instant.parse("2026-01-01T00:00:00Z")
    }
}

private class GoogleGatewayFake : GoogleOAuthGateway {
    var identity: GoogleIdentity? = null
    override suspend fun exchangeCode(code: String, codeVerifier: String, redirectUri: String) = identity
}
