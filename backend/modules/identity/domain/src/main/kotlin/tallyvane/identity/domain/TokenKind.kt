package tallyvane.identity.domain

/**
 * Which half of a token pair a [TokenValue] is — the tag a `TokenFactory` stamps into the token's
 * own prefix, so a value arriving at any later port can be told apart without a second parameter
 * naming it.
 *
 * [prefix] carries no product name (`no-hardcoded-product-name`): it is a type tag, not a secret,
 * and naming the product in it would tie every token in the system to a name that might change.
 */
public enum class TokenKind(public val prefix: String) {
    ACCESS("access"),
    REFRESH("refresh"),
}
