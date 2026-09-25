import { Text } from "frontend-shared/ui/text";
import type { AuthPageKind } from "../../../features/authentication/model/AuthPageKind";
import type { AuthStepProps } from "../model/AuthStepProps";
import { CredentialStep } from "./CredentialStep";
import { AuthenticatorEnrollmentStep, FactorVerificationStep, GoogleStep, PreviewStep } from "./FactorSteps";
import { OtpStep, PasswordRecoveryStep } from "./OtpStep";
import { SecuritySettingsStep } from "./SecuritySettingsStep";

export function renderAuthenticationStep(kind: AuthPageKind, props: AuthStepProps) {
    switch (kind) {
        case "login":
        case "register":
            return <CredentialStep kind={kind} props={props} />;
        case "mfa":
            return <FactorVerificationStep props={props} />;
        case "enrollment":
            return <AuthenticatorEnrollmentStep props={props} />;
        case "google":
            return <GoogleStep props={props} />;
        case "security":
            return <SecuritySettingsStep props={props} />;
        case "otp":
            return <OtpStep props={props} />;
        case "forgot":
            return <PasswordRecoveryStep props={props} />;
        case "callback":
            return <Text variant="body" role="status">{props.t("callbackChecking")}</Text>;
        case "preview":
            return <PreviewStep props={props} />;
    }
}
