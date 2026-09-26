package tallyvane.identity.domain.token

/**
 * The two tokens issued together at sign-in or refresh: the short-lived [access] token presented
 * on every request, and the long-lived [refresh] token presented only to mint the next pair.
 */
public data class TokenPair(public val access: TokenValue, public val refresh: TokenValue)
