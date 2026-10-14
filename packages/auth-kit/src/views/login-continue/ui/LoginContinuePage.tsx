import { authStrings, type AuthTexts, type SignInFrame } from "../../../shared/texts";
import { ContinueSignIn } from "./ContinueSignIn";

export interface LoginContinuePageProps {
    readonly texts: AuthTexts;
    /** The page the screen is drawn in; the app's own. */
    readonly frame: SignInFrame;
    /** Where a finished sign-in goes when the person was not headed anywhere in particular. */
    readonly landing: string;
}

export function LoginContinuePage({ texts, frame: Frame, landing }: LoginContinuePageProps) {
    const t = authStrings(texts, "continue");
    return (
        <Frame title={t("title")} lead={t("working")}>
            <ContinueSignIn texts={texts} landing={landing} />
        </Frame>
    );
}
