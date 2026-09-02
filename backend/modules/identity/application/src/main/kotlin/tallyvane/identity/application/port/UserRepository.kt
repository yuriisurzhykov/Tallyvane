package tallyvane.identity.application.port

import tallyvane.identity.domain.Email
import tallyvane.identity.domain.User
import tallyvane.identity.domain.UserId

/**
 * Where a [User] lives.
 *
 * [insert] is the only way a new row is created, and it reports [InsertOutcome.EMAIL_TAKEN]
 * rather than being preceded by a [findByEmail] check: under `READ COMMITTED`, two concurrent
 * registrations for the same address would both read "free" and both insert — measured in this
 * project's own persistence practice. Uniqueness is enforced by the table's own unique index,
 * never by a check that ran first.
 */
public interface UserRepository {
    public suspend fun findByEmail(email: Email): User?

    public suspend fun findById(id: UserId): User?

    public suspend fun insert(user: User): InsertOutcome

    public enum class InsertOutcome { INSERTED, EMAIL_TAKEN }
}
