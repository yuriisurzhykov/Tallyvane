package tallyvane.authentication.domain

import kotlin.time.Instant

/**
 * The only thing a method tells the rest of the system: a factor of this kind was verified at
 * this instant.
 *
 * There is no field for *which* account or *how*. The account belongs to the attempt that collects
 * these, and the how belongs to the method that produced one; keeping both out is what makes a new
 * method a new implementation rather than a change to sign-in.
 *
 * Both values are `internal`: [Attempt] reads them to answer its own questions, and nothing outside
 * this module can.
 */
public data class VerifiedFactor(internal val kind: FactorKind, internal val at: Instant)
