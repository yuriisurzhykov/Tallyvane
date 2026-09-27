export interface SettingsSectionDefinition {
    /** Stable identifier used by the application's route resolver. */
    readonly id: string;
    /** Route owned by the consuming application. */
    readonly href: string;
    readonly label: string;
    readonly description?: string;
}

/**
 * Declares an application's section list and catches duplicate identifiers
 * before navigation can become ambiguous. Section content and route handling
 * stay in the consuming application.
 */
export function defineSettingsSections<const Sections extends readonly SettingsSectionDefinition[]>(
    sections: Sections,
): Sections {
    const ids = new Set<string>();

    for (const section of sections) {
        if (!section.id.trim()) throw new Error("A settings section needs a non-empty id.");
        if (!section.href.trim()) throw new Error(`Settings section '${section.id}' needs a route.`);
        if (ids.has(section.id)) throw new Error(`Duplicate settings section id '${section.id}'.`);
        ids.add(section.id);
    }

    return sections;
}
