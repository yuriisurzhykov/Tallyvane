package tallyvane.identity.application.google

import tallyvane.identity.domain.credential.GoogleSubject
import tallyvane.identity.domain.user.Email

/**
 * What a verified Google ID token vouches for — the fact both
 * [tallyvane.identity.application.port.GoogleIdTokenVerifier] and
 * [tallyvane.identity.application.port.GoogleOAuthGateway] hand back once a token is confirmed
 * genuine and current.
 */
public data class GoogleIdentity(public val subject: GoogleSubject, public val email: Email)
