import type { Metadata } from "next";

export const metadata: Metadata = {
    title: "Design system — Tallyvane",
    robots: { index: false, follow: false },
};

/**
 * Every token the design system publishes, on one page.
 *
 * It exists to be looked at by a person and photographed by a machine. A change
 * to a colour role shows up here as a visual diff in the screenshot suite, and
 * a contrast regression shows up as a failure in the accessibility suite —
 * neither of which can happen while the only place a token exists is a CSS
 * variable nothing renders.
 *
 * Two constraints shape how it is written. Every class name is a literal:
 * Tailwind generates utilities by scanning source text, so `bg-${role}` would
 * produce nothing at all, and spelling each one out is also what makes this
 * page a genuine test of the adapter — a role the bridge failed to register
 * renders as an unstyled swatch rather than passing unnoticed. And nothing here
 * moves, randomises or reads a clock, because a screenshot baseline is only
 * worth having if the same input produces the same pixels.
 */

const SURFACES = [
    ["surfacePrimary", "bg-surface-primary"],
    ["surfaceElevated", "bg-surface-elevated"],
    ["surfaceInset", "bg-surface-inset"],
    ["surfaceRowHover", "bg-surface-row-hover"],
    ["surfaceSelected", "bg-surface-selected"],
    ["surfaceOverlay", "bg-surface-overlay"],
] as const;

const TEXT_ROLES = [
    ["textPrimary", "text-text-primary"],
    ["textSecondary", "text-text-secondary"],
    ["textMuted", "text-text-muted"],
    ["textDisabled", "text-text-disabled"],
] as const;

const BORDERS = [
    ["borderSubtle", "border-border-subtle"],
    ["borderDefault", "border-border-default"],
    ["borderStrong", "border-border-strong"],
    ["borderFocus", "border-border-focus"],
] as const;

const INTERACTIVE = [
    ["interactivePrimary", "bg-interactive-primary"],
    ["interactivePrimaryHover", "bg-interactive-primary-hover"],
    ["interactivePrimaryPressed", "bg-interactive-primary-pressed"],
    ["interactivePrimarySubtle", "bg-interactive-primary-subtle"],
] as const;

const STATUSES = [
    ["success", "bg-status-success", "bg-status-success-subtle", "text-status-success-text"],
    ["danger", "bg-status-danger", "bg-status-danger-subtle", "text-status-danger-text"],
    ["attention", "bg-status-attention", "bg-status-attention-subtle", "text-status-attention-text"],
    ["info", "bg-status-info", "bg-status-info-subtle", "text-status-info-text"],
] as const;

const TEXT_STYLES = [
    ["display", "text-display", "Large analytics figures"],
    ["title1", "text-title1", "Screen heading"],
    ["title2", "text-title2", "Section heading"],
    ["title3", "text-title3", "Card heading"],
    ["body", "text-body", "Body copy, the default"],
    ["bodyStrong", "text-body-strong", "Body copy, emphasised"],
    ["small", "text-small", "Dense tables and metadata"],
    ["caption", "text-caption", "Captions and footnotes"],
    ["overline", "text-overline", "Small capitalised heading"],
    ["numeric", "text-numeric", "$185,000 — 2026-08-18 — 0O"],
] as const;

const SPACING = [
    ["inlineTight", "w-inline-tight"],
    ["inline", "w-inline"],
    ["stackTight", "w-stack-tight"],
    ["stack", "w-stack"],
    ["groupGap", "w-group-gap"],
    ["sectionGap", "w-section-gap"],
    ["screenPadding", "w-screen-padding"],
] as const;

const RADII = [
    ["chip", "rounded-chip"],
    ["control", "rounded-control"],
    ["card", "rounded-card"],
    ["surface", "rounded-surface"],
    ["pill", "rounded-pill"],
] as const;

const ELEVATIONS = [
    ["elevation1", "shadow-elevation1"],
    ["elevation2", "shadow-elevation2"],
    ["elevation3", "shadow-elevation3"],
] as const;

const STACKING = [
    "background", "content", "sidebar", "fab", "popover", "scrim", "modal", "toast", "tooltip",
] as const;

function Section({ id, title, note, children }: {
    readonly id: string;
    readonly title: string;
    readonly note?: string;
    readonly children: React.ReactNode;
}) {
    return (
        <section id={id} data-testid={`section-${id}`} className="flex flex-col gap-stack">
            <div className="flex flex-col gap-inline-tight">
                <h2 className="text-title2">{title}</h2>
                {note ? <p className="text-small text-text-secondary">{note}</p> : null}
            </div>
            {children}
        </section>
    );
}

/** A swatch is a bordered block rather than a bare fill: several roles are translucent overlays, and without an edge an eight-percent white on a dark page is indistinguishable from nothing at all. */
function Swatch({ label, className }: { readonly label: string; readonly className: string }) {
    return (
        <div className="flex flex-col gap-inline-tight">
            <div className={`h-stack-tight rounded-control border border-border-subtle ${className}`} />
            <code className="text-caption text-text-secondary">{label}</code>
        </div>
    );
}

export default function DesignSystemStorybook() {
    return (
        <main className="mx-auto flex max-w-(--layout-console-max-width) flex-col gap-section-gap p-screen-padding">
            <header className="flex flex-col gap-inline">
                <p className="text-overline text-text-muted">Tallyvane</p>
                <h1 className="text-display">Design system</h1>
                <p className="text-body text-text-secondary">
                    Every published token, rendered. Both themes and three viewports are captured
                    from this page by the screenshot suite, and its contrast is measured by the
                    accessibility suite.
                </p>
            </header>

            <Section id="surfaces" title="Surfaces" note="The page sits below the cards on it, so a card reads as raised through value alone rather than through a shadow.">
                <div className="grid grid-cols-3 gap-group-gap">
                    {SURFACES.map(([label, className]) => <Swatch key={label} label={label} className={className} />)}
                </div>
            </Section>

            <Section id="text-roles" title="Text roles">
                <div className="flex flex-col gap-stack-tight">
                    {TEXT_ROLES.map(([label, className]) => (
                        <p key={label} className={`text-body ${className}`}>
                            {label} — the quick brown fox jumps over the lazy dog
                        </p>
                    ))}
                </div>
            </Section>

            <Section id="borders" title="Borders">
                <div className="grid grid-cols-4 gap-group-gap">
                    {BORDERS.map(([label, className]) => (
                        <div key={label} className="flex flex-col gap-inline-tight">
                            <div className={`h-stack rounded-card border-2 ${className}`} />
                            <code className="text-caption text-text-secondary">{label}</code>
                        </div>
                    ))}
                </div>
            </Section>

            <Section id="interactive" title="Interactive" note="The accent is monochrome, so it inverts between themes: near-white on dark, near-black on light. Amber is reserved for attention and never used here.">
                <div className="flex flex-col gap-stack">
                    <div className="grid grid-cols-4 gap-group-gap">
                        {INTERACTIVE.map(([label, className]) => <Swatch key={label} label={label} className={className} />)}
                    </div>
                    <div className="flex flex-wrap items-center gap-inline">
                        <span className="rounded-control bg-interactive-primary px-stack py-inline text-body-strong text-text-on-accent">
                            Primary action
                        </span>
                        <span className="rounded-control border border-border-default px-stack py-inline text-body-strong text-text-primary">
                            Secondary action
                        </span>
                        <span className="rounded-control bg-interactive-primary-subtle px-stack py-inline text-body-strong text-interactive-primary-text">
                            Quiet action
                        </span>
                        <span className="focus-ring rounded-control border border-border-default px-stack py-inline text-body-strong text-text-primary">
                            Focused
                        </span>
                    </div>
                </div>
            </Section>

            <Section id="statuses" title="Statuses" note="Three roles each, and they are not interchangeable: the fill, a wash to sit behind it, and a text colour dark or light enough to read.">
                <div className="flex flex-col gap-stack-tight">
                    {STATUSES.map(([label, fill, subtle, text]) => (
                        <div key={label} className="flex items-center gap-inline">
                            <span className={`inline-block size-inline rounded-pill ${fill}`} />
                            <span
                                className={`rounded-pill ${subtle} ${text} text-caption`}
                                style={{
                                    paddingInline: "var(--ds-component-status-badge-padding-x)",
                                    paddingBlock: "var(--ds-component-status-badge-padding-y)",
                                }}
                            >
                                {label}
                            </span>
                            <span className={`text-small ${text}`}>text on the page</span>
                            <span className={`rounded-control ${fill} text-caption text-text-on-solid px-inline py-inline-tight`}>
                                text on solid
                            </span>
                        </div>
                    ))}
                </div>
            </Section>

            <Section id="text-styles" title="Text styles" note="Each sets size, leading, weight and tracking together, so three of the four cannot be applied without the fourth.">
                <div className="flex flex-col gap-stack-tight">
                    {TEXT_STYLES.map(([label, className, sample]) => (
                        <div key={label} className="flex flex-col gap-inline-tight border-b border-border-subtle pb-stack-tight">
                            <code className="text-caption text-text-muted">{label}</code>
                            <p className={className}>{sample}</p>
                        </div>
                    ))}
                </div>
            </Section>

            <Section id="spacing" title="Spacing" note="Named by job rather than by size: padding inside a component, the gap between stacked elements, the gap between regions. This is also the layer a density setting would act on.">
                <div className="flex flex-col gap-inline-tight">
                    {SPACING.map(([label, className]) => (
                        <div key={label} className="flex items-center gap-inline">
                            <span className={`inline-block h-inline bg-interactive-primary ${className}`} />
                            <code className="text-caption text-text-secondary">{label}</code>
                        </div>
                    ))}
                </div>
            </Section>

            <Section id="radii" title="Radii" note="Rising with the size of the thing they round, so a card inside a container never looks rounder than the container.">
                <div className="grid grid-cols-5 gap-group-gap">
                    {RADII.map(([label, className]) => (
                        <div key={label} className="flex flex-col gap-inline-tight">
                            <div className={`h-stack border border-border-strong bg-surface-elevated ${className}`} />
                            <code className="text-caption text-text-secondary">{label}</code>
                        </div>
                    ))}
                </div>
            </Section>

            <Section id="elevation" title="Elevation" note="Spent only on what floats above content it did not lay out. A card in the page flow gets none.">
                <div className="grid grid-cols-3 gap-group-gap">
                    {ELEVATIONS.map(([label, className]) => (
                        <div key={label} className={`rounded-card bg-surface-elevated p-stack ${className}`}>
                            <code className="text-caption text-text-secondary">{label}</code>
                        </div>
                    ))}
                </div>
            </Section>

            <Section id="component-tokens" title="Component tokens" note="Values belonging to exactly one component. Rendered here from their variables directly, since the components themselves do not exist yet.">
                <div className="flex flex-col gap-stack">
                    <div className="flex items-start gap-inline">
                        <div
                            className="bg-border-default"
                            style={{
                                width: "var(--ds-component-timeline-connector-width)",
                                background: "var(--ds-component-timeline-connector-color)",
                                height: "var(--ds-semantic-spacing-section-gap)",
                            }}
                        />
                        <div className="flex flex-col gap-inline-tight">
                            <code className="text-caption text-text-secondary">timelineConnector</code>
                            <p className="text-small text-text-secondary">A border that happens to run vertically.</p>
                        </div>
                    </div>
                </div>
            </Section>

            <Section id="stacking" title="Stacking order" note="The whole ladder, in order. A component reaching for a number instead decides an argument the rest of the interface was never told about.">
                <ol className="flex flex-col gap-inline-tight">
                    {STACKING.map((layer, index) => (
                        <li key={layer} className="flex items-center gap-inline text-small">
                            <span className="text-numeric text-text-muted">{index}</span>
                            <code className="text-text-secondary">{layer}</code>
                        </li>
                    ))}
                </ol>
            </Section>
        </main>
    );
}
