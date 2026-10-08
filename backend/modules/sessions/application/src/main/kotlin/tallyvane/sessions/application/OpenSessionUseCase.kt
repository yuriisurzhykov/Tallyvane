package tallyvane.sessions.application

import tallyvane.authentication.contract.Proof
import tallyvane.authentication.contract.Redemption
import tallyvane.authentication.contract.SignIns
import tallyvane.identity.contract.AccountId
import tallyvane.identity.contract.Admins
import tallyvane.journal.contract.DeviceFacts
import tallyvane.journal.contract.SecurityJournal
import tallyvane.platform.kernel.Clock
import tallyvane.platform.kernel.Secret
import tallyvane.platform.kernel.Surface
import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import tallyvane.sessions.application.port.Sessions
import tallyvane.sessions.domain.Browser
import tallyvane.sessions.domain.ClientType
import tallyvane.sessions.domain.Device
import tallyvane.sessions.domain.Factor
import tallyvane.sessions.domain.Lifetimes
import tallyvane.sessions.domain.Platform
import tallyvane.sessions.domain.Session
import tallyvane.sessions.domain.UserAgent
import kotlin.time.Instant

/**
 * A person whose sign-in is complete asks for a session.
 *
 * Takes the completed sign-in from `authentication` and keeps a session under a secret nobody has seen
 * before, in one transaction: either the sign-in is gone and the session exists, or neither happened. A
 * secret that is new, never the sign-in's own, is what keeps someone who planted a known secret before
 * the sign-in from holding the session afterwards (ADR-079). The journal is told in the same transaction
 * (ADR-095).
 */
public interface OpenSessionUseCase : UseCase {
    /**
     * @param attempt The secret from the browser's `__Host-attempt` cookie, or null when it sent none.
     * @param agent What the browser said about itself, which the session keeps as the device it is on.
     * @param surface The door the request came through. It decides which sign-in is taken, the person's own
     * or an administrator's, and which kind of session is kept (ADR-097).
     */
    public suspend fun open(attempt: Secret?, agent: UserAgent, surface: Surface): Opened

    public class OpenSession(
        private val signIns: SignIns,
        private val admins: Admins,
        private val sessions: Sessions,
        private val journal: SecurityJournal,
        private val words: DeviceWords,
        private val transactions: TransactionRunner,
        private val clock: Clock,
        private val keys: SessionKeys,
    ) : OpenSessionUseCase {
        override suspend fun open(attempt: Secret?, agent: UserAgent, surface: Surface): Opened {
            val secret = attempt ?: return Opened.Failed.NothingToOpen()
            val client = ClientType.on(surface)
            return transactions.inTransaction {
                val outcome = redeemed(secret, surface).reportTo(Beginning(clock.now(), agent.device(), client))
                if (outcome is Opened.Issued) Verdict.Commit(outcome) else Verdict.Rollback(outcome)
            }
        }

        private fun redeemed(secret: Secret, surface: Surface): Redemption = when (surface) {
            Surface.App -> signIns.redeem(secret)
            Surface.Admin -> signIns.redeemAdminLogin(secret)
        }

        /**
         * What to do with each answer `authentication` can give: keep a session for a sign-in, and
         * for nothing, nothing. An administrator's session is kept only for an account that holds the right.
         */
        private inner class Beginning(
            private val now: Instant,
            private val device: Device,
            private val client: ClientType,
        ) : Redemption.Report<Opened> {
            override fun redeemed(account: AccountId, proofs: Set<Proof>, authenticatedAt: Instant): Opened {
                if (client == ClientType.Admin && !admins.isAdmin(account)) return Opened.Failed.NotAnAdministrator()
                val issued = keys.issue()
                sessions.add(
                    issued.key,
                    Session.begin(
                        issued.id,
                        account.value,
                        proofs.mapTo(mutableSetOf(), ::factorOf),
                        client,
                        device,
                        authenticatedAt,
                        now,
                    ),
                )
                journal.signedIn(account, issued.id.value, FactsOf().of(device))
                return Opened.Issued(issued.secret, Lifetimes.LONGEST)
            }

            override fun nothingToRedeem(): Opened = Opened.Failed.NothingToOpen()
        }

        /**
         * A device as the journal is told of it: in the words the API uses.
         */
        private inner class FactsOf : Device.Record {
            private val told = mutableListOf<DeviceFacts>()

            override fun device(browser: Browser, platform: Platform, mobile: Boolean, name: String?) {
                told += DeviceFacts(words.of(browser), words.of(platform), mobile, name)
            }

            fun of(device: Device): DeviceFacts {
                device.writeTo(this)
                return told.single()
            }
        }

        private fun factorOf(proof: Proof): Factor = when (proof) {
            Proof.Google -> Factor.Google
            Proof.Totp -> Factor.Totp
            Proof.RecoveryCode -> Factor.RecoveryCode
        }

        override fun toString(): String = "OpenSession(sessions=$sessions)"
    }
}
