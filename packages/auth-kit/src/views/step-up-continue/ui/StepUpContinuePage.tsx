import { authStrings, type AuthTexts, type SignInFrame } from "../../../shared/texts";
import { StepUpContinuation } from "./StepUpContinuation";

export interface StepUpContinuePageProps {
    readonly texts: AuthTexts;
    /** The page the screen is drawn in; the app's own. */
    readonly frame: SignInFrame;
}

export function StepUpContinuePage({ texts, frame: Frame }: StepUpContinuePageProps) {
    const t = authStrings(texts, "stepUpContinue");
    return (
        <Frame title={t("title")} lead={t("lead")}>
            <StepUpContinuation texts={texts} />
        </Frame>
    );
}
