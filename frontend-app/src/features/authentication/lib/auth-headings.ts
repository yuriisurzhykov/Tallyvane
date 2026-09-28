import type { AuthPageKind } from "../model/AuthPageKind";
import type { AuthStringKey } from "../model/strings";

export const AUTH_HEADINGS = {
    login: ["loginTitle", "loginDescription"],
    register: ["registerTitle", "registerDescription"],
    mfa: ["mfaTitle", "mfaTitleHelp"],
    enrollment: ["enrollTitle", "enrollmentDescription"],
    forgot: ["forgotTitle", "forgotHelp"],
    otp: ["verifyTitle", "otpHelp"],
    google: ["google", "googleAvailableHelp"],
    callback: ["oauthErrorTitle", "oauthErrorDescription"],
    security: ["security", "securityDescription"],
    preview: ["demo", "previewDescription"],
} as const satisfies Record<AuthPageKind, readonly [AuthStringKey, AuthStringKey]>;