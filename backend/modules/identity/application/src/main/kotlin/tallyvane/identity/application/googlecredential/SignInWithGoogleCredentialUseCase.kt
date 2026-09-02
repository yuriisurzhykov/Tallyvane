package tallyvane.identity.application.googlecredential

import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.application.google.GoogleSignInCompleter
import tallyvane.identity.application.port.GoogleIdTokenVerifier
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.platform.kernel.UseCase

/**
 * Signs in with a Google Identity Services ID token — the browser's own Credential Manager hands
 * this token straight to the client, no code exchange, so this use case has only a token to
 * verify — one action, per ADR-053, distinct from
 * [tallyvane.identity.application.googleoauth.SignInWithGoogleOAuthUseCase].
 */
public interface SignInWithGoogleCredentialUseCase : UseCase {
    public suspend fun signIn(request: SignInWithGoogleCredentialRequest): SignInOutcome

    /**
     * Verifies the presented token, then hands the identity to [completer] — the same sequence
     * [tallyvane.identity.application.googleoauth.SignInWithGoogleOAuthUseCase.SignIn] uses once
     * it has its own verified identity.
     */
    public class SignIn internal constructor(
        private val verifier: GoogleIdTokenVerifier,
        private val completer: GoogleSignInCompleter,
    ) : SignInWithGoogleCredentialUseCase {
        override suspend fun signIn(request: SignInWithGoogleCredentialRequest): SignInOutcome {
            val identity = verifier.verify(request.idToken)
            return if (identity == null) {
                SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)
            } else {
                completer.complete(identity, request.device)
            }
        }
    }
}
