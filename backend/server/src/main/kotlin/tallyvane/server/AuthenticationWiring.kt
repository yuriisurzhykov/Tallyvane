package tallyvane.server

import tallyvane.authentication.application.ActivePolicies
import tallyvane.authentication.application.AttemptOwners
import tallyvane.authentication.application.BeginSignInUseCase
import tallyvane.authentication.application.BeginStepUpUseCase
import tallyvane.authentication.application.BeginTotpUseCase
import tallyvane.authentication.application.ConfirmTotpUseCase
import tallyvane.authentication.application.ContinueWithGoogleUseCase
import tallyvane.authentication.application.Departures
import tallyvane.authentication.application.DisableTotpUseCase
import tallyvane.authentication.application.Enrollments
import tallyvane.authentication.application.GoogleTrips
import tallyvane.authentication.application.RecoveryCodeWords
import tallyvane.authentication.application.Redemptions
import tallyvane.authentication.application.RegenerateRecoveryCodesUseCase
import tallyvane.authentication.application.RegisterUseCase
import tallyvane.authentication.application.ShowRegistrationUseCase
import tallyvane.authentication.application.ShowSecondFactorUseCase
import tallyvane.authentication.application.ShowSignInUseCase
import tallyvane.authentication.application.SignInKeys
import tallyvane.authentication.application.VerifySecondFactorUseCase
import tallyvane.authentication.application.port.Google
import tallyvane.authentication.application.port.RecoveryCodeMint
import tallyvane.authentication.application.port.SeedSource
import tallyvane.authentication.contract.SignIns
import tallyvane.authentication.infrastructure.AuthenticationStorageFactory
import tallyvane.authentication.infrastructure.GoogleAccessFactory
import tallyvane.authentication.web.AuthenticationRoutesFactory
import tallyvane.authentication.web.SecondFactorRoutesFactory
import tallyvane.journal.contract.SecurityJournal
import tallyvane.platform.http.RouteModule
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Digests
import tallyvane.platform.kernel.SecretGenerator
import tallyvane.server.config.SignInConfiguration

/**
 * `authentication`: how a person proves who they are: the first Google sign-in, and a second step with
 * TOTP or a recovery code (ADR-093).
 *
 * Everything is deferred, so construction touches no database and makes no call to Google.
 */
public class AuthenticationWiring(
    private val platform: PlatformWiring,
    identity: IdentityWiring,
    private val journal: SecurityJournal,
    private val settings: SignInConfiguration,
) {
    // Opens the keyset now, so a variable that does not hold one stops the server at start and not at the
    // first person who turns TOTP on.
    private val storage = AuthenticationStorageFactory(platform.ids, settings.totpKeyset)

    private val clock: Clock = Clock.Wall()

    private val digests = Digests.Hmac(settings.tokenPepper, settings.pepperVersion)

    private val keys = SignInKeys(SecretGenerator.Csprng(), digests)

    private val google: Google by lazy {
        GoogleAccessFactory().google(settings.googleClientId, settings.googleClientSecret, settings.redirectUri())
    }

    private val totp = storage.totpEnrollments()

    private val recoveryCodes = storage.recoveryCodeSets()

    private val owners = AttemptOwners(identity.accounts)

    private val policies = ActivePolicies(storage.policyVersions(), owners, Enrollments(totp, recoveryCodes))

    private val words = RecoveryCodeWords()

    private val seeds: SeedSource = SeedSource.Csprng()

    private val mint: RecoveryCodeMint = RecoveryCodeMint.Csprng()

    private val trips = GoogleTrips(
        attempts = storage.attempts(),
        handshakes = storage.googleHandshakes(),
        profiles = storage.googleProfiles(),
        accounts = identity.accounts,
        transactions = platform.persistence.transactions,
    )

    private val departures by lazy { Departures(trips, google, clock, keys) }

    private val begin: BeginSignInUseCase by lazy { BeginSignInUseCase.BeginSignIn(departures) }

    private val beginStepUp: BeginStepUpUseCase by lazy { BeginStepUpUseCase.BeginStepUp(departures) }

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

    private val showSignIn: ShowSignInUseCase by lazy {
        ShowSignInUseCase.ShowSignIn(storage.attempts(), policies, platform.persistence.transactions, clock, keys)
    }

    private val verify: VerifySecondFactorUseCase by lazy {
        VerifySecondFactorUseCase.VerifySecondFactor(
            attempts = storage.attempts(),
            policies = policies,
            owners = owners,
            totp = totp,
            codes = recoveryCodes,
            failures = storage.accountFailures(),
            words = words,
            digests = digests,
            transactions = platform.persistence.transactions,
            clock = clock,
            keys = keys,
            journal = journal,
        )
    }

    private val beginTotp: BeginTotpUseCase by lazy {
        BeginTotpUseCase.BeginTotp(totp, seeds, settings.totpIssuer, platform.persistence.transactions)
    }

    private val confirmTotp: ConfirmTotpUseCase by lazy {
        ConfirmTotpUseCase.ConfirmTotp(
            totp,
            recoveryCodes,
            mint,
            words,
            digests,
            platform.persistence.transactions,
            journal,
            clock,
        )
    }

    private val disableTotp: DisableTotpUseCase by lazy {
        DisableTotpUseCase.DisableTotp(totp, journal, platform.persistence.transactions)
    }

    private val regenerateRecoveryCodes: RegenerateRecoveryCodesUseCase by lazy {
        RegenerateRecoveryCodesUseCase.RegenerateRecoveryCodes(
            totp,
            recoveryCodes,
            mint,
            words,
            digests,
            journal,
            platform.persistence.transactions,
        )
    }

    private val showSecondFactor: ShowSecondFactorUseCase by lazy {
        ShowSecondFactorUseCase.ShowSecondFactor(totp, recoveryCodes, platform.persistence.transactions)
    }

    /**
     * Completed sign-ins and confirmations, as `sessions` takes them.
     */
    public val signIns: SignIns by lazy {
        Redemptions(storage.attempts(), policies, identity.accounts, clock, keys)
    }

    /**
     * The routes this module serves.
     */
    public val routes: List<RouteModule> by lazy {
        val web = AuthenticationRoutesFactory()
        val secondFactor = SecondFactorRoutesFactory()
        listOf(
            web.signIn(begin),
            web.stepUp(beginStepUp),
            web.googleReturn(continueWith, settings.appOrigin),
            web.welcome(show),
            web.registration(register),
            secondFactor.signInState(showSignIn),
            secondFactor.secondFactorCodes(verify),
            secondFactor.secondFactor(showSecondFactor),
            secondFactor.totpEnrollments(beginTotp),
            secondFactor.totpConfirmations(confirmTotp),
            secondFactor.totpEnrollment(disableTotp),
            secondFactor.recoveryCodes(regenerateRecoveryCodes),
        )
    }

    override fun toString(): String = "AuthenticationWiring"
}
