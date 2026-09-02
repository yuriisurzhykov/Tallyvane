package tallyvane.identity.domain

import tallyvane.platform.kernel.Secret

/**
 * A [TokenValue] after a `TokenHasher` has hashed it under a server-side pepper — the only form of
 * a token this module is ever allowed to write to storage or compare against what storage
 * returned.
 *
 * [hash] is a [Secret] rather than a bare `String` on purpose: comparing two of these must run in
 * constant time regardless of how many leading characters match, the property
 * `backend/.plans/backend-access-and-api.md` §7.3 names as a requirement for every token in this
 * system, not only the service health token ADR-063 already gave it to. [Secret] already carries
 * exactly that comparison, so this type does not re-derive it.
 *
 * @property pepperVersion which pepper produced [hash] — the field that will let a future
 * `TokenHasher` verify a token hashed before a rotation without also accepting one forged under a
 * guessed pepper.
 */
public data class HashedToken(public val hash: Secret, public val pepperVersion: Int)
