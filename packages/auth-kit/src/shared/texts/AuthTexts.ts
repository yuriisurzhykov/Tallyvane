import type { ReactNode } from "react";
import { createUseStrings } from "frontend-shared/i18n";

/** The keys of every namespace the sign-in screens read. Words are the app's; which words are needed is the package's. */
interface AuthKeys {
    auth: "continueWithGoogle";
    login:
        | "title"
        | "lead"
        | "fineprint"
        | "failed"
        | "problemRestart"
        | "problemExpired"
        | "problemCancelled"
        | "problemRefused"
        | "problemEmailUnverified"
        | "problemUnavailable";
    continue: "title" | "working" | "failed" | "back" | "closeTab" | "ended" | "blocked";
    verify: "title" | "lead" | "working" | "failed" | "ended" | "blocked" | "startAgain";
    secondFactor:
        | "codeLabel"
        | "codeHint"
        | "recoveryLabel"
        | "recoveryHint"
        | "wrongCode"
        | "wrongRecovery"
        | "paused"
        | "pausedAnnouncement"
        | "failed"
        | "submit"
        | "useRecovery"
        | "useApp";
    recoveryUsed: "used" | "noneLeft" | "oneLeft" | "manyLeft" | "retired" | "setUp" | "continue";
    reauth: "title" | "lead" | "blocked" | "openLink" | "different" | "failed";
    stepUp: "title" | "lead" | "blocked" | "openLink" | "failed" | "cancel";
    stepUpContinue: "title" | "working" | "done" | "failed" | "otherAccount" | "lead" | "ended" | "recoveryUsed";
}

/**
 * Every word the sign-in screens show, supplied by the app that renders them. A key left out is a compile
 * error in the app, so an app cannot ship a screen with a hole in it. `{name}` placeholders are filled in
 * by the lookup, as everywhere else.
 */
export type AuthTexts = { readonly [Namespace in keyof AuthKeys]: Readonly<Record<AuthKeys[Namespace], string>> };

/** The lookup of one namespace of the supplied words, `t(key, vars?)`. */
export function authStrings<N extends keyof AuthTexts>(texts: AuthTexts, namespace: N) {
    return createUseStrings(texts)(namespace);
}

export interface SignInFrameProps {
    readonly title: string;
    /** One sentence under the title. */
    readonly lead: ReactNode;
    readonly children: ReactNode;
}

/** The page every signed-out screen is drawn in. The app owns it: its mark, its card, its links. */
export type SignInFrame = (props: SignInFrameProps) => ReactNode;
