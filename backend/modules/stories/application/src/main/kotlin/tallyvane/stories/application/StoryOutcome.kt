package tallyvane.stories.application

import tallyvane.platform.kernel.Failure
import tallyvane.stories.domain.Story

/**
 * What telling or correcting a story ended in.
 */
public sealed interface StoryOutcome {
    public class Told internal constructor(private val story: Story) : StoryOutcome {
        public fun writeTo(record: Story.Record) {
            story.writeTo(record)
        }

        internal fun saveTo(stories: tallyvane.stories.application.port.Stories) {
            stories.save(story)
        }

        override fun equals(other: Any?): Boolean = other is Told && other.story == story

        override fun hashCode(): Int = story.hashCode()

        override fun toString(): String = "Told($story)"
    }

    public sealed interface Failed :
        StoryOutcome,
        Failure {
        /**
         * Nothing was said: the words were blank or only invisible characters.
         */
        public class Empty internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is Empty

            override fun hashCode(): Int = Empty::class.hashCode()

            override fun toString(): String = "Empty"
        }

        /**
         * No such story or fact for this person.
         */
        public class Missing internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is Missing

            override fun hashCode(): Int = Missing::class.hashCode()

            override fun toString(): String = "Missing"
        }

        /**
         * The correction did not quote the new words, so nothing was changed.
         */
        public class Unheard internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is Unheard

            override fun hashCode(): Int = Unheard::class.hashCode()

            override fun toString(): String = "Unheard"
        }

        /**
         * The model could not be asked. The words are kept all the same.
         */
        public class Unavailable internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is Unavailable

            override fun hashCode(): Int = Unavailable::class.hashCode()

            override fun toString(): String = "Unavailable"
        }

        /**
         * Today's model allowance is spent. The words are kept all the same.
         */
        public class OverBudget internal constructor() : Failed {
            override fun equals(other: Any?): Boolean = other is OverBudget

            override fun hashCode(): Int = OverBudget::class.hashCode()

            override fun toString(): String = "OverBudget"
        }
    }
}
