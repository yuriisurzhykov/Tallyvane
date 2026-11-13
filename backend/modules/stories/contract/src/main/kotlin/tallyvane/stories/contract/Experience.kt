package tallyvane.stories.contract

import kotlin.uuid.Uuid

/**
 * What a person has told about their work, for the modules that build on it: the route and the résumé.
 */
public interface Experience {
    /**
     * Every story of [account], the newest first, with its live facts only.
     */
    public suspend fun of(account: Uuid): List<ToldStory>
}
