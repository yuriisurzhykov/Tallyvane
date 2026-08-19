/*
 * AUTO-GENERATED FILE. DO NOT EDIT MANUALLY.
 * Source: frontend/src/shared/ui/theme/{tokens,contracts,themes,composites}/
 * Generator: frontend/scripts/generate-design-tokens.ts
 *
 * Already-resolved token data, holding no `{reference}` strings and needing
 * no compiler to read. This is the only token artefact a non-CSS consumer
 * may import.
 */
export const resolved = {
    "light": {
        "color": {
            "surfacePage": "hsl(30 7% 97%)",
            "surfaceCard": "hsl(30 8% 99%)",
            "surfaceSunken": "hsl(0 0% 0% / 4%)",
            "surfaceHover": "hsl(0 0% 0% / 4%)",
            "surfaceOverlay": "hsl(30 8% 99% / 80%)",
            "textPrimary": "hsl(30 4% 15%)",
            "textSecondary": "hsl(30 4% 31%)",
            "textMuted": "hsl(30 3% 42%)",
            "textOnAccent": "hsl(30 8% 99%)",
            "borderSubtle": "hsl(0 0% 0% / 8%)",
            "borderDefault": "hsl(0 0% 0% / 12%)",
            "borderStrong": "hsl(0 0% 0% / 24%)",
            "borderFocus": "hsl(30 4% 15%)",
            "accent": "hsl(30 4% 15%)",
            "accentHover": "hsl(30 5% 7%)",
            "accentSubtle": "hsl(0 0% 0% / 8%)",
            "statusAttention": "hsl(37 82% 30%)",
            "statusAttentionSubtle": "hsl(37 75% 64% / 14%)",
            "statusSuccess": "hsl(131 58% 44%)",
            "statusSuccessSubtle": "hsl(131 53% 67% / 14%)",
            "statusDanger": "hsl(0 68% 42%)",
            "statusDangerSubtle": "hsl(0 74% 60% / 12%)",
            "statusInfo": "hsl(205 28% 38%)",
            "statusInfoSubtle": "hsl(205 26% 48% / 12%)"
        },
        "component": {
            "statusChip": {
                "radius": "9999px",
                "paddingInline": "0.5rem",
                "paddingBlock": "0.25rem"
            },
            "timelineConnector": {
                "color": "hsl(0 0% 0% / 12%)",
                "width": "1px"
            }
        },
        "composite": {
            "shadow": {
                "overlayPanel": "0px 1px 2px 0px hsl(30 5% 7% / 8%), 0px 8px 24px -4px hsl(30 5% 7% / 12%)",
                "focusRing": "0px 0px 0px 2px hsl(30 4% 15%)"
            },
            "textStyle": {
                "pageTitle": {
                    "size": "2rem",
                    "line": "1.15",
                    "weight": "600",
                    "tracking": "-0.02em"
                },
                "sectionTitle": {
                    "size": "1.25rem",
                    "line": "1.35",
                    "weight": "600",
                    "tracking": "-0.015em"
                },
                "cardTitle": {
                    "size": "1.0625rem",
                    "line": "1.35",
                    "weight": "500",
                    "tracking": "-0.015em"
                },
                "body": {
                    "size": "1rem",
                    "line": "1.5",
                    "weight": "400",
                    "tracking": "0"
                },
                "bodySmall": {
                    "size": "0.875rem",
                    "line": "1.5",
                    "weight": "400",
                    "tracking": "0"
                },
                "label": {
                    "size": "0.8125rem",
                    "line": "1.35",
                    "weight": "500",
                    "tracking": "0.06em"
                },
                "caption": {
                    "size": "0.6875rem",
                    "line": "1.5",
                    "weight": "400",
                    "tracking": "0"
                },
                "numeric": {
                    "family": "var(--font-ibm-plex-mono), ui-monospace, SFMono-Regular, monospace",
                    "size": "0.875rem",
                    "line": "1.5",
                    "weight": "400",
                    "tracking": "0"
                }
            },
            "transition": {
                "hover": {
                    "duration": "180ms",
                    "easing": "cubic-bezier(0.2, 0, 0, 1)"
                },
                "press": {
                    "duration": "180ms",
                    "easing": "cubic-bezier(0.4, 0, 1, 1)"
                },
                "enter": {
                    "duration": "240ms",
                    "easing": "cubic-bezier(0, 0, 0, 1)"
                },
                "exit": {
                    "duration": "180ms",
                    "easing": "cubic-bezier(0.4, 0, 1, 1)"
                }
            }
        }
    },
    "dark": {
        "color": {
            "surfacePage": "hsl(30 5% 7%)",
            "surfaceCard": "hsl(30 4% 10%)",
            "surfaceSunken": "hsl(0 0% 100% / 4%)",
            "surfaceHover": "hsl(0 0% 100% / 4%)",
            "surfaceOverlay": "hsl(30 5% 7% / 80%)",
            "textPrimary": "hsl(30 7% 97%)",
            "textSecondary": "hsl(30 5% 80%)",
            "textMuted": "hsl(30 4% 68%)",
            "textOnAccent": "hsl(30 5% 7%)",
            "borderSubtle": "hsl(0 0% 100% / 8%)",
            "borderDefault": "hsl(0 0% 100% / 12%)",
            "borderStrong": "hsl(0 0% 100% / 24%)",
            "borderFocus": "hsl(30 7% 97%)",
            "accent": "hsl(30 7% 97%)",
            "accentHover": "hsl(30 8% 99%)",
            "accentSubtle": "hsl(0 0% 100% / 8%)",
            "statusAttention": "hsl(37 72% 74%)",
            "statusAttentionSubtle": "hsl(37 75% 64% / 18%)",
            "statusSuccess": "hsl(131 52% 75%)",
            "statusSuccessSubtle": "hsl(131 53% 67% / 18%)",
            "statusDanger": "hsl(0 78% 76%)",
            "statusDangerSubtle": "hsl(0 74% 60% / 16%)",
            "statusInfo": "hsl(205 22% 70%)",
            "statusInfoSubtle": "hsl(205 26% 48% / 16%)"
        },
        "component": {
            "statusChip": {
                "radius": "9999px",
                "paddingInline": "0.5rem",
                "paddingBlock": "0.25rem"
            },
            "timelineConnector": {
                "color": "hsl(0 0% 100% / 12%)",
                "width": "1px"
            }
        },
        "composite": {
            "shadow": {
                "overlayPanel": "0px 1px 2px 0px hsl(30 5% 7% / 8%), 0px 8px 24px -4px hsl(30 5% 7% / 12%)",
                "focusRing": "0px 0px 0px 2px hsl(30 7% 97%)"
            },
            "textStyle": {
                "pageTitle": {
                    "size": "2rem",
                    "line": "1.15",
                    "weight": "600",
                    "tracking": "-0.02em"
                },
                "sectionTitle": {
                    "size": "1.25rem",
                    "line": "1.35",
                    "weight": "600",
                    "tracking": "-0.015em"
                },
                "cardTitle": {
                    "size": "1.0625rem",
                    "line": "1.35",
                    "weight": "500",
                    "tracking": "-0.015em"
                },
                "body": {
                    "size": "1rem",
                    "line": "1.5",
                    "weight": "400",
                    "tracking": "0"
                },
                "bodySmall": {
                    "size": "0.875rem",
                    "line": "1.5",
                    "weight": "400",
                    "tracking": "0"
                },
                "label": {
                    "size": "0.8125rem",
                    "line": "1.35",
                    "weight": "500",
                    "tracking": "0.06em"
                },
                "caption": {
                    "size": "0.6875rem",
                    "line": "1.5",
                    "weight": "400",
                    "tracking": "0"
                },
                "numeric": {
                    "family": "var(--font-ibm-plex-mono), ui-monospace, SFMono-Regular, monospace",
                    "size": "0.875rem",
                    "line": "1.5",
                    "weight": "400",
                    "tracking": "0"
                }
            },
            "transition": {
                "hover": {
                    "duration": "180ms",
                    "easing": "cubic-bezier(0.2, 0, 0, 1)"
                },
                "press": {
                    "duration": "180ms",
                    "easing": "cubic-bezier(0.4, 0, 1, 1)"
                },
                "enter": {
                    "duration": "240ms",
                    "easing": "cubic-bezier(0, 0, 0, 1)"
                },
                "exit": {
                    "duration": "180ms",
                    "easing": "cubic-bezier(0.4, 0, 1, 1)"
                }
            }
        }
    }
} as const;
