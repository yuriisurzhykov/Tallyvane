/**
 * The literal `[role, className, ...]` tuples every section in
 * `token-catalog-sections.tsx` renders from. Every class name here is a
 * literal, not a template interpolation: Tailwind generates utilities by
 * scanning source text, so `bg-${role}` would produce nothing at all, and
 * spelling each one out is also what makes the catalog page a genuine test
 * of the adapter — a role the bridge failed to register renders as an
 * unstyled swatch rather than passing unnoticed.
 */

export const SURFACES = [
    ["surfacePrimary", "bg-surface-primary"],
    ["surfaceElevated", "bg-surface-elevated"],
    ["surfaceInset", "bg-surface-inset"],
    ["surfaceRowHover", "bg-surface-row-hover"],
    ["surfaceSelected", "bg-surface-selected"],
    ["surfaceOverlay", "bg-surface-overlay"],
] as const;

export const TEXT_ROLES = [
    ["textPrimary", "text-text-primary"],
    ["textSecondary", "text-text-secondary"],
    ["textMuted", "text-text-muted"],
] as const;

export const BORDERS = [
    ["borderSubtle", "border-border-subtle"],
    ["borderDefault", "border-border-default"],
    ["borderStrong", "border-border-strong"],
    ["borderFocus", "border-border-focus"],
] as const;

export const INTERACTIVE = [
    ["interactivePrimary", "bg-interactive-primary"],
    ["interactivePrimaryHover", "bg-interactive-primary-hover"],
    ["interactivePrimaryPressed", "bg-interactive-primary-pressed"],
    ["interactivePrimarySubtle", "bg-interactive-primary-subtle"],
] as const;

export const STATUSES = [
    ["success", "bg-status-success", "bg-status-success-subtle", "text-status-success-text"],
    ["danger", "bg-status-danger", "bg-status-danger-subtle", "text-status-danger-text"],
    ["attention", "bg-status-attention", "bg-status-attention-subtle", "text-status-attention-text"],
    ["info", "bg-status-info", "bg-status-info-subtle", "text-status-info-text"],
] as const;

export const TEXT_STYLES = [
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

export const SPACING = [
    ["inlineTight", "w-inline-tight"],
    ["inline", "w-inline"],
    ["stackTight", "w-stack-tight"],
    ["stack", "w-stack"],
    ["groupGap", "w-group-gap"],
    ["sectionGap", "w-section-gap"],
    ["screenPadding", "w-screen-padding"],
] as const;

export const RADII = [
    ["chip", "rounded-chip"],
    ["control", "rounded-control"],
    ["card", "rounded-card"],
    ["surface", "rounded-surface"],
    ["pill", "rounded-pill"],
] as const;

export const ELEVATIONS = [
    ["elevation1", "shadow-elevation1"],
    ["elevation2", "shadow-elevation2"],
    ["elevation3", "shadow-elevation3"],
] as const;

export const STACKING = [
    "background", "content", "sidebar", "fab", "popover", "scrim", "modal", "toast", "tooltip",
] as const;
