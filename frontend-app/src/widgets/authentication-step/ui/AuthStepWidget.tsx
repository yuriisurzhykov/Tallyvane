import { Link } from "frontend-shared/ui/link";
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
            return <Link href="/login">{props.t("backLogin")}</Link>;
        case "preview":
            return <PreviewStep props={props} />;
    }
}
