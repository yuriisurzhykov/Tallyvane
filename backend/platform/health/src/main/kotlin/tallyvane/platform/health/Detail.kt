package tallyvane.platform.health

import kotlinx.serialization.Serializable

/**
 * What an authorised reader gets: the breakdown, per ADR-055.
 *
 * `ready` is repeated here although it is formally derivable from the checks, because the
 * derivation is not obvious — a `down` optional dependency leaves the application ready — and
 * making a reader redo that logic is an invitation to get it wrong.
 */
@Serializable
internal data class Detail(val status: String, val ready: Boolean, val checks: List<Checked>) {
    /**
     * One dependency's answer. `tookMs` is an integer of milliseconds: an alert threshold reads
     * `3`, not `0.003`, and no reader has to parse a duration format.
     */
    @Serializable
    internal data class Checked(val name: String, val status: String, val tookMs: Long, val cause: Cause? = null)
}
