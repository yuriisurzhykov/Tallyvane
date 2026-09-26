package tallyvane.identity.web.oauth

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.domain.session.SessionId
import tallyvane.identity.domain.user.UserId
import kotlin.uuid.Uuid

class GoogleOAuthStateCookieSpec :
    StringSpec({
        "reauthentication state binds its verifier to the account and active session" {
            val userId = UserId(Uuid.parse("00000000-0000-7000-8000-000000000121"))
            val sessionId = SessionId(Uuid.parse("00000000-0000-7000-8000-000000000122"))
            val challenge = PkceChallenge.Generated("fresh-state", "pkce-verifier", "pkce-challenge")
            val cookie = GoogleOAuthStateCookie.Cookie()
            val value = cookie.reauthenticate(challenge, userId, sessionId)

            cookie.read(value, "fresh-state") shouldBe GoogleOAuthStateCookie.State.Reauthenticate(
                "pkce-verifier",
                userId,
                sessionId,
            )
            cookie.read(value, "attacker-state") shouldBe null
        }
    })
