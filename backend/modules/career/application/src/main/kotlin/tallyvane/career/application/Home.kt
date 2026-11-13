package tallyvane.career.application

/**
 * The home screen: one main step, the route to the goal, and quiet rows below.
 *
 * [step] is one of: `tell_first`, `set_goal`, `finish_story`, `close_gap`, `keep_going`. [subject] is what the
 * step is about (a story id or a skill slug), [detail] what to ask (a slot) or the skill's name.
 */
@Suppress("LongParameterList") // What the home shows.
public class Home(
    public val step: String,
    public val subject: String?,
    public val detail: String?,
    public val goal: String?,
    public val goalName: String?,
    public val route: List<Stop>,
    public val stories: Int,
) {
    override fun toString(): String = "Home($step)"

    /**
     * One skill on the route, how important the role makes it, and where the person stands on it.
     */
    public class Stop(
        public val skill: String,
        public val name: String,
        public val importance: Double,
        public val level: Level,
        public val evidence: Int,
    ) {
        override fun toString(): String = "Stop($skill, $level)"
    }
}
