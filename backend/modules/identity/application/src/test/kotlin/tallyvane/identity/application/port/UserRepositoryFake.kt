package tallyvane.identity.application.port

import tallyvane.identity.domain.user.Email
import tallyvane.identity.domain.user.User
import tallyvane.identity.domain.user.UserId

internal class UserRepositoryFake : UserRepository {
    private val users = mutableMapOf<UserId, User>()

    override suspend fun findByEmail(email: Email): User? = users.values.find { it.email == email }

    override suspend fun findById(id: UserId): User? = users[id]

    override suspend fun insert(user: User): UserRepository.InsertOutcome {
        if (users.values.any { it.email == user.email }) {
            return UserRepository.InsertOutcome.EMAIL_TAKEN
        }
        users[user.id] = user
        return UserRepository.InsertOutcome.INSERTED
    }
}
