package tallyvane.journal.web

import kotlinx.serialization.Serializable

/**
 * What `GET /security-activity` answers: a page of entries, the newest first, and the cursor to ask for the
 * next one, or null on the last page.
 */
@Serializable
internal class ActivityShown(val entries: List<EntryShown>, val next: String?)
