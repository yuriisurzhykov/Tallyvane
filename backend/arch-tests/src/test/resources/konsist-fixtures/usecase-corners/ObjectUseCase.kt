package tallyvane.jobs.application

import tallyvane.platform.kernel.UseCase

// Forbidden for the same reason as a top-level class, and reached by a different
// declaration kind: `classes()` does not return objects.
object SignOut : UseCase {
    suspend fun signOut(session: SessionId): Unit = TODO()
}
