package tallyvane.authentication.application.port

import tallyvane.authentication.application.GoogleProfile
import tallyvane.platform.kernel.Digest

/**
 * Where what Google said about a new person waits for them to finish the welcome screen.
 *
 * The name and the address are personal data of somebody who has not agreed to anything yet. They
 * live beside the registration attempt, under its key, and go when it does (slice 3, fork 3): an
 * attempt lasts minutes, and the account that keeps them for good is created only with consent.
 *
 * Both methods run inside the caller's transaction (ADR-052).
 */
public interface GoogleProfiles {
    /**
     * Keeps [profile] for the attempt kept under [attempt], which must already be kept.
     */
    public fun keep(attempt: Digest, profile: GoogleProfile)

    /**
     * The profile kept for the attempt under [attempt], or null when there is none.
     */
    public fun of(attempt: Digest): GoogleProfile?
}
