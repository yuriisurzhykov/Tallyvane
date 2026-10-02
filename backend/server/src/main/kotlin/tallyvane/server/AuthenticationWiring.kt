package tallyvane.server

import tallyvane.authentication.application.ActivePolicies
import tallyvane.authentication.application.BeginSignInUseCase
import tallyvane.authentication.application.ContinueWithGoogleUseCase
import tallyvane.authentication.application.GoogleTrips
import tallyvane.authentication.application.RegisterUseCase
import tallyvane.authentication.application.ShowRegistrationUseCase
import tallyvane.authentication.application.SignInKeys
import tallyvane.authentication.application.port.Google
import tallyvane.authentication.infrastructure.AuthenticationStorageFactory
import tallyvane.authentication.infrastructure.GoogleAccessFactory
import tallyvane.authentication.web.AuthenticationRoutesFactory
import tallyvane.platform.http.RouteModule
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Digests
import tallyvane.platform.kernel.SecretGenerator
import tallyvane.server.config.SignInConfiguration

/**
 * `authentication`: how a person proves who they are, as far as the first Google sign-in goes.
 *
 * Everything is deferred, so construction touches no database and makes no call to Google.
 */
public class AuthenticationWiring(
    private val platform: PlatformWiring,
    identity: IdentityWiring,
    private val settings: SignInConfiguration,
) {
    private val storage = AuthenticationStorageFactory(platform.ids)

    private val clock: Clock = Clock.Wall()

    private val keys = SignInKeys(SecretGenerator.Csprng(), Digests.Hmac(settings.tokenPepper, settings.pepperVersion))

    private val google: Google by lazy {
        GoogleAccessFactory().google(settings.googleClientId, settings.googleClientSecret, settings.redirectUri())
    }

    private val policies = ActivePolicies(storage.policyVersions())

    private val trips = GoogleTrips(
        attempts = storage.attempts(),
        handshakes = storage.googleHandshakes(),
        profiles = storage.googleProfiles(),
        accounts = identity.accounts,
        transactions = platform.persistence.transactions,
    )

    private val begin: BeginSignInUseCase by lazy { BeginSignInUseCase.BeginSignIn(trips, google, clock, keys) }

    private val continueWith: ContinueWithGoogleUseCase by lazy {
        ContinueWithGoogleUseCase.ContinueWithGoogle(trips, google, policies, clock, keys)
    }

    private val show: ShowRegistrationUseCase by lazy {
        ShowRegistrationUseCase.ShowRegistration(
            storage.attempts(),
            storage.googleProfiles(),
            policies,
            platform.persistence.transactions,
            clock,
            keys,
        )
    }

    private val register: RegisterUseCase by lazy {
        RegisterUseCase.Register(
            storage.attempts(),
            storage.googleProfiles(),
            policies,
            identity.accounts,
            platform.persistence.transactions,
            clock,
            keys,
        )
    }

    /**
     * The routes this module serves.
     */
    public val routes: List<RouteModule> by lazy {
        val web = AuthenticationRoutesFactory()
        listOf(
            web.signIn(begin),
            web.googleReturn(continueWith, settings.appOrigin),
            web.welcome(show),
            web.registration(register),
        )
    }

    override fun toString(): String = "AuthenticationWiring"
}
