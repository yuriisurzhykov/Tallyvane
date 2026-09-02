package tallyvane.identity.application.port

import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId

/**
 * Where a [User] lives.
 */
public interface UserRepository {
    public suspend fun findByEmail(email: Email): User?

    public suspend fun findById(id: UserId): User?

    /**
     * The only way a new row is created. Reports [InsertOutcome.EMAIL_TAKEN] instead of being
     * preceded by a [findByEmail] check: under `READ COMMITTED`, two concurrent registrations for
     * the same address would both read "free" and both insert. Uniqueness is enforced by the
     * table's own unique index, never by a check that ran first.
     */
    public suspend fun insert(user: User): InsertOutcome

    public enum class InsertOutcome { INSERTED, EMAIL_TAKEN }
}
