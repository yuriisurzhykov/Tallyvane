package tallyvane.identity.application.googlecredential

import io.kotest.core.spec.style.StringSpec
import io.kotest.matchers.shouldBe
import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.application.google.GoogleIdentity
import tallyvane.identity.application.google.GoogleSignInCompleter
import tallyvane.identity.application.port.GoogleIdTokenVerifier
import tallyvane.identity.domain.credential.GoogleSubject
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.identity.domain.session.DeviceLabel
import tallyvane.identity.domain.user.Email

class SignInSpec :
    StringSpec({
        val identity = GoogleIdentity(GoogleSubject("108234567890123456789"), Email("person@example.com"))
        val device = DeviceLabel("Chrome on MacBook")
        val request = SignInWithGoogleCredentialRequest(idToken = "genuine-id-token", device)

        // Anonymous objects, not named classes: `port-has-conformance-suite` counts named
        // implementations across main and test, and each of these already has a real one.
        fun verifierAnswering(identity: GoogleIdentity?): GoogleIdTokenVerifier = object : GoogleIdTokenVerifier {
            override suspend fun verify(idToken: String) = identity
        }

        fun completerAnswering(outcome: SignInOutcome): GoogleSignInCompleter = object : GoogleSignInCompleter {
            override suspend fun complete(identity: GoogleIdentity, device: DeviceLabel) = outcome
        }

        "a token the verifier resolves is handed to the completer, whose answer passes through unchanged" {
            val issued = SignInOutcome.NotIssued(AuthenticationOutcome.RateLimited)
            val signIn =
                SignInWithGoogleCredentialUseCase.SignIn(verifierAnswering(identity), completerAnswering(issued))

            signIn.signIn(request) shouldBe issued
        }

        "a token the verifier does not recognise is refused without ever reaching the completer" {
            var completerCalled = false
            val completer =
                object : GoogleSignInCompleter {
                    override suspend fun complete(identity: GoogleIdentity, device: DeviceLabel): SignInOutcome {
                        completerCalled = true
                        return SignInOutcome.NotIssued(AuthenticationOutcome.RateLimited)
                    }
                }
            val signIn = SignInWithGoogleCredentialUseCase.SignIn(verifierAnswering(null), completer)

            val result = signIn.signIn(request)

            result shouldBe SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)
            completerCalled shouldBe false
        }
    })
