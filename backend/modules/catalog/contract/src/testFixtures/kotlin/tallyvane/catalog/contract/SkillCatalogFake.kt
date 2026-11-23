package tallyvane.catalog.contract

/**
 * A [SkillCatalog] of three skills and one role, for the tests of the modules that read it (ADR-044).
 */
class SkillCatalogFake : SkillCatalog {
    override fun skills(): List<SkillName> = listOf(
        SkillName("architecture", "Architecture"),
        SkillName("kotlin", "Kotlin"),
        SkillName("mentoring", "Mentoring"),
    )

    override fun roles(): List<RoleTrack> = listOf(
        RoleTrack(
            "staff-android",
            "Staff Android Engineer",
            listOf(Station("kotlin", 0.9), Station("architecture", 1.0), Station("mentoring", 0.7)),
        ),
    )

    override fun role(slug: String): RoleTrack? = roles().firstOrNull { it.slug == slug }
}
