import { createUseStrings } from "frontend-shared/i18n";

const dictionary = {
    adminAuthentication: {
        title: "Authentication",
        navPages: "Pages",
        navMedia: "Media",
        navStrings: "Strings",
        skipLink: "Skip to content",
        description: "Choose how people sign in and which second factors they need. Changes apply to future sign-ins.",
        signInPolicy: "Sign-in policy",
        password: "Password", google: "Google", emailCode: "Email sign-in code",
        authenticator: "Authenticator app", emailOtp: "Email verification code", backupCode: "Backup code",
        disabled: "No second factor", ifEnrolled: "When enrolled", required: "Always required",
        allowSignIn: "Allow {method} sign-in", secondFactorRequirement: "Second-factor requirement",
        allowedSecondFactors: "Allowed second factors",
        reloadPolicy: "Reload policy", loading: "Loading authentication policy…",
        enablePolicyError: "Enable at least one sign-in method and choose a factor for each active second-factor rule.",
        savePolicy: "Save policy", resetTitle: "Reset a user’s second factors", resetSecondFactors: "Reset second factors", accountEmail: "Account email",
        resetHelp: "This revokes sessions and removes enrolled factors. Verify the user’s identity before proceeding.",
        resetFactors: "Reset second factors", advancedWarningTitle: "Less-independent verification",
        advancedWarningBody: "Email after Google or email-code sign-in can depend on the same inbox. Someone who controls that inbox may pass both steps. Save only if you explicitly accept this risk.",
        resetConfirmationTitle: "Reset second factors?", resetConfirmationBody: "Reset all second factors for {email}? Their sessions will be revoked and they must enroll again. This cannot be undone.",
        cancel: "Cancel", acceptRiskSave: "Accept risk and save", resetAndRevoke: "Reset and revoke sessions",
        signInAdministrator: "Sign in to your administrator account.", administratorDenied: "Your account does not have administrator access.",
        signInAdminLink: "Sign in to administration",
        stalePolicy: "This policy changed elsewhere. Reload it before saving again.", requestFailed: "The request failed. Please try again.",
        connectionFailed: "Could not connect. Please try again.", settingsLoadFailed: "Authentication settings could not be loaded",
        policySaved: "Authentication policy saved", factorsReset: "Second factors reset",
        factorsResetDescription: "Sessions revoked. The user must sign in and enroll again.",
    },
} as const;

export const useAdminAuthenticationStrings = createUseStrings(dictionary);
