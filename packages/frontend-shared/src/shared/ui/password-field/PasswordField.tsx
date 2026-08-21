import { useState } from "react";
import { Eye, EyeOff } from "lucide-react";
import { Input, type InputProps, type InputSize } from "../input";
import { IconButton } from "../icon-button";
import { CONTROL_ICON_CLASS, mergeStyle } from "../../lib";

export interface PasswordFieldOwnProps {
    /** Accessible name for the toggle while the password is hidden — announces the action the click performs, not the current state. */
    readonly showPasswordLabel: string;
    /** Accessible name for the toggle while the password is shown. */
    readonly hidePasswordLabel: string;
}

/** `type` is owned by this component's own visibility state — a caller cannot override what `Input` beneath it renders as. */
export type PasswordFieldProps = PasswordFieldOwnProps & Omit<InputProps, "type">;

/**
 * `calc()` over the control-height role already used for `Input`'s and
 * `IconButton`'s own sizing, plus one `inline-tight` gap so the caret never
 * sits flush against the glyph. Tracks whichever `size` the caller picks.
 */
function toggleInsetFor(size: InputSize): string {
    return `calc(var(--control-height-${size}) + var(--spacing-inline-tight))`;
}

/**
 * Tier 0 — single-line text with a visibility toggle, per `COMPONENTS.md`.
 * Composes `Input` and `IconButton` rather than a third implementation of a
 * text box: the toggle is a real `IconButton` (`tone="ghost"`, matching its
 * own size role) laid over `Input`'s reserved trailing padding, not a fourth
 * text-input implementation of its own.
 *
 * `visible` is internal, uncontrolled state — nothing above this component
 * (`Field`, `Form`) has any reason to know or drive whether the password is
 * currently masked, since it is a purely local rendering detail orthogonal
 * to the field's value.
 */
export function PasswordField({ showPasswordLabel, hidePasswordLabel, size = "md", className, style, ...rest }: PasswordFieldProps) {
    const [visible, setVisible] = useState(false);

    return (
        <div className={["relative", className].filter(Boolean).join(" ")}>
            <Input {...rest} size={size} type={visible ? "text" : "password"} style={mergeStyle(style, { paddingInlineEnd: toggleInsetFor(size) })} />
            <IconButton
                tone="ghost"
                size={size}
                label={visible ? hidePasswordLabel : showPasswordLabel}
                aria-pressed={visible}
                onClick={() => { setVisible((current) => !current); }}
                className="absolute top-1/2 right-0 -translate-y-1/2"
            >
                {visible ? <EyeOff className={CONTROL_ICON_CLASS} /> : <Eye className={CONTROL_ICON_CLASS} />}
            </IconButton>
        </div>
    );
}
