package tallyvane.platform.health

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * `Ailment` on the wire: an object discriminated by `kind`, per ADR-055.
 *
 * A separate hierarchy from `Ailment` itself, for two reasons. The wire form is a contract slice 14
 * describes and cannot change without notice, while `Ailment` is an internal type free to grow a
 * field. And annotating `Ailment` would put a serialization library into
 * `platform:observability` — the module every producer of signals depends on, which is kept free of
 * exactly that kind of weight.
 *
 * The discriminator is `kind` rather than kotlinx's default `type` because [Threw] already has a
 * field called `type` holding an exception's class name, and because §11.6 uses `type` for an RFC
 * 9457 problem URI. `ApiJson` sets it globally.
 *
 * Note what is missing: `Ailment.Dependencies` and `Ailment.Behind` have no case here at all. They
 * name what the system is built from — its dependencies and its schema versions — and ADR-055 keeps
 * those from any answer, so there is no type that could render them. Their absence is enforced by
 * this file having no case for them, not by a filter somebody has to remember.
 */
@Serializable
internal sealed interface Cause {
    /**
     * The check decided this itself, in its own words.
     */
    @Serializable
    @SerialName("refused")
    data class Refused(val says: String) : Cause

    /**
     * Did not answer inside the bound applied to it.
     */
    @Serializable
    @SerialName("overran")
    data class Overran(val boundMs: Long) : Cause

    /**
     * Failed with this exception type. The type only: a message carries hosts, ports and
     * occasionally credentials (§17).
     */
    @Serializable
    @SerialName("threw")
    data class Threw(val type: String) : Cause
}
