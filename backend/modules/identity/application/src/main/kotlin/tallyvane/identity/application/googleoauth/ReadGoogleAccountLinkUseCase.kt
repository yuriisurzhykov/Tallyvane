package tallyvane.identity.application.googleoauth

import tallyvane.identity.application.port.CredentialRepository
import tallyvane.identity.domain.user.UserId
import tallyvane.platform.kernel.UseCase

public interface ReadGoogleAccountLinkUseCase : UseCase {
    public suspend fun isLinked(userId: UserId): Boolean

    public class Read(private val credentials: CredentialRepository) : ReadGoogleAccountLinkUseCase {
        override suspend fun isLinked(userId: UserId): Boolean = credentials.findGoogleFor(userId) != null
    }
}
