import {
    BORDERS,
    ELEVATIONS,
    INTERACTIVE,
    RADII,
    SPACING,
    STACKING,
    STATUSES,
    SURFACES,
    TEXT_ROLES,
    TEXT_STYLES,
} from "./token-catalog-data";

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

function PageHeader() {
    return (
        <header className="flex flex-col gap-inline">
            <p className="text-overline text-text-muted">Tallyvane</p>
            <h1 className="text-display">Design system</h1>
            <p className="text-body text-text-secondary">
                Every published token, rendered. Both themes and three viewports are captured
                from this page by the screenshot suite, and its contrast is measured by the
                accessibility suite.
            </p>
        </header>
    );
}

function SurfacesSection() {
    return (
        <Section id="surfaces" title="Surfaces" note="The page sits below the cards on it, so a card reads as raised through value alone rather than through a shadow.">
            <div className="grid grid-cols-3 gap-group-gap">
                {SURFACES.map(([label, className]) => <Swatch key={label} label={label} className={className} />)}
            </div>
        </Section>
    );
}

function TextRolesSection() {
    return (
        <Section id="text-roles" title="Text roles" note="`textDisabled` is shown on a genuinely disabled control rather than as a paragraph. Its whole job is to look unavailable, so both contrast models exempt it — but only when the markup says it is disabled, which is also what a screen reader reads.">
            <div className="flex flex-col gap-stack-tight">
                {TEXT_ROLES.map(([label, className]) => (
                    <p key={label} className={`text-body ${className}`}>
                        {label} — the quick brown fox jumps over the lazy dog
                    </p>
                ))}
                <button type="button" disabled className="self-start rounded-control border border-border-subtle px-stack py-inline text-body text-text-disabled">
                    textDisabled — an unavailable action
                </button>
            </div>
        </Section>
    );
}

function BordersSection() {
    return (
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
    );
}

function InteractiveSection() {
    return (
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
    );
}

function StatusesSection() {
    return (
        <Section id="statuses" title="Statuses" note="Three roles each, and they are not interchangeable: the fill, a wash to sit behind it, and a text colour dark or light enough to read.">
            <div className="flex flex-col gap-stack-tight">
                {STATUSES.map(([label, fill, subtle, text]) => (
                    <div key={label} className="flex items-center gap-inline">
                        {/* The dot takes the TEXT colour, not the fill. A fill is
                            deep enough to carry white text, which makes it nearly
                            invisible as a dot on a dark page — the two roles look
                            interchangeable and are not. */}
                        <span className={`inline-block size-inline rounded-pill ${text.replace("text-", "bg-")}`} />
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
    );
}

function TextStylesSection() {
    return (
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
    );
}

function SpacingSection() {
    return (
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
    );
}

function RadiiSection() {
    return (
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
    );
}

function ElevationSection() {
    return (
        <Section id="elevation" title="Elevation" note="Spent only on what floats above content it did not lay out. A card in the page flow gets none.">
            <div className="grid grid-cols-3 gap-group-gap">
                {ELEVATIONS.map(([label, className]) => (
                    <div key={label} className={`rounded-card bg-surface-elevated p-stack ${className}`}>
                        <code className="text-caption text-text-secondary">{label}</code>
                    </div>
                ))}
            </div>
        </Section>
    );
}

function ComponentTokensSection() {
    return (
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
    );
}

function StackingSection() {
    return (
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
    );
}

/**
 * Every token the design system publishes, on one page.
 *
 * It exists to be looked at by a person and photographed by a machine. A change
 * to a colour role shows up here as a visual diff in the screenshot suite, and
 * a contrast regression shows up as a failure in the accessibility suite —
 * neither of which can happen while the only place a token exists is a CSS
 * variable nothing renders.
 *
 * Nothing here moves, randomises or reads a clock, because a screenshot
 * baseline is only worth having if the same input produces the same pixels.
 * The literal `[role, className, ...]` data every section below renders from
 * lives in `token-catalog-data.ts`.
 */
export function DesignSystemStorybook() {
    return (
        <main className="mx-auto flex max-w-(--layout-console-max-width) flex-col gap-section-gap p-screen-padding">
            <PageHeader />
            <SurfacesSection />
            <TextRolesSection />
            <BordersSection />
            <InteractiveSection />
            <StatusesSection />
            <TextStylesSection />
            <SpacingSection />
            <RadiiSection />
            <ElevationSection />
            <ComponentTokensSection />
            <StackingSection />
        </main>
    );
}
