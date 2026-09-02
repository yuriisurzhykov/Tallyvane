package tallyvane.identity.domain

import tallyvane.platform.kernel.Secret

/**
 * An Argon2id hash of a password — self-describing: algorithm, cost parameters, salt and hash all
 * live in the one encoded string Argon2id itself produces, unlike [HashedToken], which has to
 * carry its pepper version alongside the hash because HMAC-SHA256 output carries none of that.
 *
 * [encoded] is a [Secret], not a bare `String`, for the same reason every hash in this module is:
 * a hash is derived data, not the password itself, but it is still credential-adjacent material
 * that has no business in a log line.
 */
public data class PasswordHash(public val encoded: Secret)
