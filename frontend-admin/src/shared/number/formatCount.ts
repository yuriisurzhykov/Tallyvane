/** A count of people or sessions for a line of text, with the grouping of the screen's language: 1,204. */
export function formatCount(count: number, locale = "en"): string {
    return new Intl.NumberFormat(locale).format(count);
}
