import { useStrings } from "@/shared/i18n";
import { AuthFrame } from "@/widgets/auth-frame";
import { VerifySignIn } from "./VerifySignIn";

export function LoginVerifyPage() {
    const t = useStrings("verify");
    return (
        <AuthFrame title={t("title")} lead={t("lead")}>
            <VerifySignIn />
        </AuthFrame>
    );
}
