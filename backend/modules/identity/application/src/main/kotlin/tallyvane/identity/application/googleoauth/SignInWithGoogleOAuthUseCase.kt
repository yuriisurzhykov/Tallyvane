package tallyvane.identity.application.googleoauth

import tallyvane.identity.application.SignInOutcome
import tallyvane.identity.application.google.GoogleSignInCompleter
import tallyvane.identity.application.port.GoogleOAuthGateway
import tallyvane.identity.domain.outcome.AuthenticationOutcome
import tallyvane.platform.kernel.UseCase

/**
 * Signs in with Google's Authorization Code + PKCE flow — one action, per ADR-053, distinct from
 * [tallyvane.identity.application.password.SignInWithPasswordUseCase]: a client chooses between
 * the two by which button it shows, not by a value this use case branches on.
 */
public interface SignInWithGoogleOAuthUseCase : UseCase {
    public suspend fun signIn(request: SignInWithGoogleOAuthRequest): SignInOutcome

    /**
     * Exchanges the code for a verified [tallyvane.identity.application.google.GoogleIdentity],
     * then hands it to [completer] — the sequence Google Identity Services (`googlecredential/`)
     * also needs once it has one, which is why that part is not written here.
     */
    public class SignIn internal constructor(
        private val gateway: GoogleOAuthGateway,
        private val completer: GoogleSignInCompleter,
    ) : SignInWithGoogleOAuthUseCase {
        override suspend fun signIn(request: SignInWithGoogleOAuthRequest): SignInOutcome {
            val identity = gateway.exchangeCode(request.code, request.codeVerifier, request.redirectUri)
            return if (identity == null) {
                SignInOutcome.NotIssued(AuthenticationOutcome.InvalidCredential)
            } else {
                completer.complete(identity, request.device)
            }
        }
    }
}
