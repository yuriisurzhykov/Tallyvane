package tallyvane.journal.application.port

import tallyvane.journal.domain.Entry

/**
 * Tells the person, or whoever watches over them, that something happened to their account (ADR-083).
 *
 * Told every entry, right after it is written, and it is for the adapter to carry out what [Entry.isNotable]
 * says. It runs inside the transaction of the act, and there is no way yet to run something after the commit, so
 * an adapter must not do anything it cannot take back: a log line may stay for an act that rolled back, and a
 * message to an address must be queued, not sent (ADR-095). Nothing it is given or writes holds a token, a code
 * or a secret.
 */
public interface SecurityNotifier {
    public fun notify(entry: Entry)
}
