package tallyvane.identity.infrastructure.persistence

import java.time.OffsetDateTime
import java.time.ZoneOffset
import kotlin.time.Instant
import kotlin.time.toJavaInstant
import kotlin.time.toKotlinInstant

/**
 * Every `timestamptz` column in this module's tables converts through this — why a conversion
 * exists at all: `exposed-core` has no `kotlin.time.Instant` column type of its own, only
 * `exposed-kotlin-datetime`'s `timestamp()`, which maps to a timezone-less `TIMESTAMP`. This
 * module's migration deliberately uses `timestamptz`, matching Postgres's own documented advice
 * for an absolute instant, so the column type is `timestampWithTimeZone()` —
 * `java.time.OffsetDateTime` — and every repository converts at this one boundary instead of the
 * domain layer ever seeing that type. Kept as its own small class rather than top-level
 * functions, the same shape `Base32` already uses for a pure conversion with no state of its own.
 */
internal class InstantColumn {
    fun toColumn(instant: Instant): OffsetDateTime = instant.toJavaInstant().atOffset(ZoneOffset.UTC)

    fun toDomain(column: OffsetDateTime): Instant = column.toInstant().toKotlinInstant()
}
