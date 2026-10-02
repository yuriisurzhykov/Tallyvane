import { useStrings } from "@/shared/i18n";
import { AuthFrame } from "@/widgets/auth-frame";
import { ContinueSignIn } from "./ContinueSignIn";

export function LoginContinuePage() {
    const t = useStrings("continue");
    return (
        <AuthFrame title={t("title")} lead={t("working")}>
            <ContinueSignIn />
        </AuthFrame>
    );
}
