import { useStrings } from "@/shared/i18n";
import { ConfirmStepUp } from "@/features/reauthenticate";
import { AuthFrame } from "@/widgets/auth-frame";

export function StepUpContinuePage() {
    const t = useStrings("stepUpContinue");
    return (
        <AuthFrame title={t("title")} lead={t("working")}>
            <ConfirmStepUp />
        </AuthFrame>
    );
}
