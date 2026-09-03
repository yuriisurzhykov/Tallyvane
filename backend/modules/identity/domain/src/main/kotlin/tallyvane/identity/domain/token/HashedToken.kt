package tallyvane.identity.domain.token

import tallyvane.platform.kernel.Secret

/**
 * A [TokenValue] after a `TokenHasher` has hashed it under a server-side pepper — the only form of
 * a token this module is ever allowed to write to storage or compare against what storage
 * returned.
 *
 * [hash] is a [Secret], not a bare `String`: comparing two of these must run in constant time, and
 * [Secret] already carries that comparison. Why every token in this system needs it:
 * `domain/README.md`.
 *
 * @property pepperVersion Which pepper produced [hash] — lets a future `TokenHasher` verify a
 * token hashed before a rotation without also accepting one forged under a guessed pepper.
 */
public data class HashedToken(public val hash: Secret, public val pepperVersion: Int)
