package tallyvane.sessions.application

import tallyvane.platform.kernel.Failure
import tallyvane.sessions.domain.Browser
import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.Factor
import tallyvane.sessions.domain.Platform
import tallyvane.sessions.domain.Session
import tallyvane.sessions.domain.SessionId
import kotlin.time.Instant
import kotlin.uuid.Uuid

/**
 * How an act on a person's own sessions ended: seeing them, ending one, ending the rest, naming one
 * (ADR-090).
 *
 * One outcome for the four, because they fail in the same ways: the person is not recognised, or what
 * they pointed at is not theirs.
 */
public sealed interface DeviceOutcome {
    /**
     * The person's live sessions, the one in use among them. Tells them through [writeTo] and nothing else.
     */
    public class Listed internal constructor(private val sessions: List<Session>, private val current: SessionId) :
        DeviceOutcome {
        /**
         * Tells [record] each session, the most recently used first.
         */
        public fun writeTo(record: Record) {
            sessions
                .map { session -> Entry().also { session.writeTo(it) } }
                .sortedByDescending { it.lastActiveAt() }
                .forEach { it.tellTo(record, current) }
        }

        override fun toString(): String = "Listed(${sessions.size})"

        /**
         * Whoever shows the list, told each device.
         */
        public fun interface Record {
            public fun device(
                id: SessionId,
                browser: Browser,
                platform: Platform,
                mobile: Boolean,
                name: String?,
                authenticatedAt: Instant,
                lastActiveAt: Instant,
                current: Boolean,
            )
        }

        private class Entry : Session.Record {
            private var id: SessionId? = null
            private var authenticatedAt: Instant? = null
            private var lastActiveAt: Instant? = null
            private var browser: Browser? = null
            private var platform: Platform? = null
            private var mobile: Boolean = false
            private var name: String? = null

            override fun session(
                id: SessionId,
                account: Uuid,
                client: ClientType,
                authenticatedAt: Instant,
                lastActiveAt: Instant,
            ) {
                this.id = id
                this.authenticatedAt = authenticatedAt
                this.lastActiveAt = lastActiveAt
            }

            override fun device(browser: Browser, platform: Platform, mobile: Boolean, name: String?) {
                this.browser = browser
                this.platform = platform
                this.mobile = mobile
                this.name = name
            }

            override fun proved(factor: Factor) = Unit

            fun lastActiveAt(): Instant = checkNotNull(lastActiveAt) { "A session tells when it was last used." }

            fun tellTo(record: Record, current: SessionId) {
                val told = checkNotNull(id) { "A session tells its id." }
                record.device(
                    told,
                    checkNotNull(browser) { "A session tells its device." },
                    checkNotNull(platform) { "A session tells its device." },
                    mobile,
                    name,
                    checkNotNull(authenticatedAt) { "A session tells when it began." },
                    lastActiveAt(),
                    told == current,
                )
            }
        }
    }

    /**
     * What was asked is done: the session is ended, the others are ended, the device is named.
     */
    public class Done : DeviceOutcome {
        override fun equals(other: Any?): Boolean = other is Done

        override fun hashCode(): Int = Done::class.hashCode()

        override fun toString(): String = "Done"
    }

    public sealed interface Failed :
        DeviceOutcome,
        Failure {
        /**
         * No session was presented.
         */
        public class SignInRequired : Failed {
            override fun equals(other: Any?): Boolean = other is SignInRequired

            override fun hashCode(): Int = SignInRequired::class.hashCode()

            override fun toString(): String = "SignInRequired"
        }

        /**
         * The session presented is over, or nobody issued it.
         */
        public class SessionExpired : Failed {
            override fun equals(other: Any?): Boolean = other is SessionExpired

            override fun hashCode(): Int = SessionExpired::class.hashCode()

            override fun toString(): String = "SessionExpired"
        }

        /**
         * The person has no such session: it never existed, it already ended, or it is someone else's.
         * The three look the same, so asking about a stranger's id teaches nothing.
         */
        public class NoSuchDevice : Failed {
            override fun equals(other: Any?): Boolean = other is NoSuchDevice

            override fun hashCode(): Int = NoSuchDevice::class.hashCode()

            override fun toString(): String = "NoSuchDevice"
        }

        /**
         * The name is not one a device may have. Nothing was changed.
         */
        public class NameRefused : Failed {
            override fun equals(other: Any?): Boolean = other is NameRefused

            override fun hashCode(): Int = NameRefused::class.hashCode()

            override fun toString(): String = "NameRefused"
        }
    }
}
