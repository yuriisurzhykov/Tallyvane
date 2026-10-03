package tallyvane.sessions.application

import tallyvane.identity.contract.AccountDeleted
import tallyvane.identity.contract.AccountId
import tallyvane.platform.events.EventSubscriber
import tallyvane.sessions.application.port.Sessions
import kotlin.reflect.KClass

/**
 * Answers an account being deleted by ending every session it had (ADR-090).
 *
 * Runs in the transaction that deletes the account, so the account and its sessions go in one commit and
 * no session is left acting for someone who is not there. The sessions that survive a missed event would
 * still be refused by `GET /me`, but only that one route would notice.
 */
public class SessionsOfDeletedAccounts(private val sessions: Sessions) : EventSubscriber<AccountDeleted> {
    override val eventType: KClass<AccountDeleted> = AccountDeleted::class

    override fun on(event: AccountDeleted) {
        event.reportTo { account: AccountId -> sessions.revokeAll(account.value) }
    }

    override fun toString(): String = "SessionsOfDeletedAccounts($sessions)"
}
