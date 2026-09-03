package tallyvane.identity.application

import tallyvane.identity.domain.session.Session
import tallyvane.identity.domain.token.TokenPair

/**
 * What [SessionIssuer] hands back: the persisted [session] record, and the one and only moment
 * [tokens]' raw values exist inside `identity`'s own code at all — the caller (a use case's route,
 * eventually) is responsible for whatever it does with them next and never reads them back out of
 * storage, because storage never gets them in raw form.
 */
public data class IssuedSession(public val session: Session, public val tokens: TokenPair)
