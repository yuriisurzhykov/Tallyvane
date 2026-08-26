package tallyvane.platform.kernel

/**
 * Marks the branch of an operation's outcome that says the operation did not happen.
 *
 * Lives here rather than in `platform:http` for one reason: the HTTP layer must be able to
 * accept a module's failure without knowing anything about the module, and `platform:http`
 * may not import a capability (`platform-knows-no-business`). A marker in the kernel gives
 * `Problems<F : Failure>` something to bound its type parameter with, and costs the kernel
 * no dependency at all.
 *
 * It carries no members on purpose. A failure's meaning belongs to the module that declared
 * it; anything this interface required would be a guess made by the platform.
 *
 * ```kotlin
 * sealed interface SaveJobOutcome {
 *     data class Saved(val id: JobId) : SaveJobOutcome
 *
 *     sealed interface Failed : SaveJobOutcome, Failure {
 *         data class RangeInvalid(val min: Money, val max: Money) : Failed
 *         data class NotYours(val id: JobId) : Failed
 *     }
 * }
 * ```
 *
 * Grouping every failure under one branch is not decoration: it is what lets a module write
 * one mapping table instead of one per case, and `outcome-groups-failures` enforces it.
 */
public interface Failure
