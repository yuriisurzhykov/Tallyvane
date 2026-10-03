import { useStrings } from "@/shared/i18n";
import { AuthFrame } from "@/widgets/auth-frame";
import { StepUpContinuation } from "./StepUpContinuation";

export function StepUpContinuePage() {
    const t = useStrings("stepUpContinue");
    return (
        <AuthFrame title={t("title")} lead={t("lead")}>
            <StepUpContinuation />
        </AuthFrame>
    );
}
