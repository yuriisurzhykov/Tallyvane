package tallyvane.catalog.contract

/**
 * The curated list of skills and roles, the same for every account.
 *
 * It is the vocabulary the model is given and the only source of a skill's slug: a slug a reply names that is
 * not here is dropped by whoever reads the reply.
 */
public interface SkillCatalog {
    public fun skills(): List<SkillName>

    public fun roles(): List<RoleTrack>

    /**
     * The role with that slug, or null.
     */
    public fun role(slug: String): RoleTrack?
}
