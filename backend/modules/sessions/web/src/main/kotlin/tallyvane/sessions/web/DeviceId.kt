package tallyvane.sessions.web

import io.ktor.server.application.ApplicationCall
import tallyvane.sessions.domain.SessionId
import kotlin.uuid.Uuid

/**
 * The device a request's address names: the `{id}` of the path.
 */
internal class DeviceId {
    /**
     * The id in the path, or null when it is not an id at all, which is as good as naming a device that
     * is not there.
     */
    fun of(call: ApplicationCall): SessionId? = call.parameters["id"]?.let(Uuid::parseOrNull)?.let(::SessionId)

    override fun toString(): String = "DeviceId"
}
