import { authStrings, type AuthTexts, type SignInFrame } from "../../../shared/texts";
import { VerifySignIn } from "./VerifySignIn";

export interface LoginVerifyPageProps {
    readonly texts: AuthTexts;
    /** The page the screen is drawn in; the app's own. */
    readonly frame: SignInFrame;
    /** Where the notice about a spent recovery code sends the person to set the authenticator up again; none, no such button. */
    readonly recoverySetUpAt?: string;
}

export function LoginVerifyPage({ texts, frame: Frame, recoverySetUpAt }: LoginVerifyPageProps) {
    const t = authStrings(texts, "verify");
    return (
        <Frame title={t("title")} lead={t("lead")}>
            <VerifySignIn texts={texts} recoverySetUpAt={recoverySetUpAt} />
        </Frame>
    );
}
