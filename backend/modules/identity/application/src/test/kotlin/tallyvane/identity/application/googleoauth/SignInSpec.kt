package tallyvane.identity.application.googleoauth

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.application.google.GoogleIdentity
import tallyvane.identity.application.google.GoogleSignInCompleter
import tallyvane.identity.application.port.GoogleOAuthGateway
import tallyvane.identity.domain.credential.GoogleSubject
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.Email

class SignInSpec :
    StringSpec({
        val identity = GoogleIdentity(GoogleSubject("108234567890123456789"), Email("person@example.com"))
        val device = DeviceLabel("Chrome on MacBook")
        val request =
            SignInWithGoogleOAuthRequest("auth-code", codeVerifier = "verifier", "https://app/callback", device)

        // Anonymous objects, not named classes: `port-has-conformance-suite` counts named
        // implementations across main and test, and each of these already has a real one.
        fun gatewayAnswering(identity: GoogleIdentity?): GoogleOAuthGateway = object : GoogleOAuthGateway {
            override suspend fun exchangeCode(code: String, codeVerifier: String, redirectUri: String) = identity
        }

        fun completerAnswering(outcome: SignInOutcome): GoogleSignInCompleter = object : GoogleSignInCompleter {
            override suspend fun complete(identity: GoogleIdentity, device: DeviceLabel) = outcome
        }

        "a code the gateway resolves is handed to the completer, whose answer passes through unchanged" {
            val issued = SignInOutcome.NotIssued(AuthenticationOutcome.RateLimited)
            val signIn = SignInWithGoogleOAuthUseCase.SignIn(gatewayAnswering(identity), completerAnswering(issued))

            signIn.signIn(request) shouldBe issued
        }

        "a code the gateway does not recognise is refused without ever reaching the completer" {
            var completerCalled = false
            val completer =
                object : GoogleSignInCompleter {
                    override suspend fun complete(identity: GoogleIdentity, device: DeviceLabel): SignInOutcome {
                        completerCalled = true
                        return SignInOutcome.NotIssued(AuthenticationOutcome.RateLimited)
                    }
                }
            val signIn = SignInWithGoogleOAuthUseCase.SignIn(gatewayAnswering(null), completer)

            val result = signIn.signIn(request)

            result shouldBe SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)
            completerCalled shouldBe false
        }
    })
