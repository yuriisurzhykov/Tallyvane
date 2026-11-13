package tallyvane.stories.application

import tallyvane.platform.kernel.TransactionRunner
import tallyvane.platform.kernel.UseCase
import tallyvane.platform.kernel.Verdict
import tallyvane.stories.application.port.Stories
import kotlin.uuid.Uuid

/**
 * Shows a person the stories they told: what they are made of, and what is still to be asked.
 */
public interface ShowStoriesUseCase : UseCase {
    public suspend fun show(account: Uuid): Shelf

    public class ShowStories(private val stories: Stories, private val transactions: TransactionRunner) :
        ShowStoriesUseCase {
        override suspend fun show(account: Uuid): Shelf =
            transactions.inTransaction { Verdict.Commit(Shelf(stories.of(account))) }

        override fun toString(): String = "ShowStories(stories=$stories)"
    }
}
