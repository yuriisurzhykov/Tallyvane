package tallyvane.platform.http

import io.ktor.server.application.ApplicationCall
import tallyvane.platform.idempotency.Owner

/**
 * Who a request's `Idempotency-Key` belongs to: the subject its authentication established, or nobody.
 *
 * A key is private to its owner, so one person's key reveals nothing of another's (ADR-086). The edge
 * asks before the request runs and cannot know what authentication will conclude, so the answer comes
 * from here, and the session slice supplies the implementation that reads it.
 */
public fun interface Owners {
    public fun of(call: ApplicationCall): Owner

    /**
     * Everyone is anonymous, which is true until sessions exist. Their keys then share one namespace,
     * which is acceptable only while the answers that matter most, the ones that carry a credential,
     * are never stored.
     */
    public class Anonymous : Owners {
        override fun of(call: ApplicationCall): Owner = Owner.anonymous()
    }
}
