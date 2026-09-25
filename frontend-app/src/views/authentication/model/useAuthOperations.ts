import type { SyntheticEvent } from "react";
import type { AuthStringKey } from "../../../features/authentication/model/strings";
import type { AuthPageKind } from "../../../features/authentication/model/AuthPageKind";
import type { useAuthStrings } from "../../../features/authentication/model/strings";
import type { AuthOperationsState } from "./authOperations";
import { submitAuthForm } from "./authOperations";
import { enrollEmailFactor, issueRecoveryCodes, resendRegistration, revokeAuthSession } from "./authAccountOperations";
import type { AuthPageState } from "./useAuthPageState";

type Translate = ReturnType<typeof useAuthStrings>;
type Notify = (title: string, description: string | undefined, tone: "attention" | "danger" | "success") => void;

function localizedFieldErrors(errors: Readonly<Record<string, string>>, t: Translate) {
    return Object.fromEntries(Object.keys(errors).map(fieldName => {
        const key: AuthStringKey = fieldName === "email" ? "invalidEmail" :
            fieldName.toLowerCase().includes("password") ? "invalidPassword" :
                fieldName.toLowerCase().includes("code") ? "invalidCode" : "invalidField";
        return [fieldName, t(key)];
    }));
}

export function useAuthOperations(
    options: {
        readonly kind: AuthPageKind;
        readonly t: Translate;
        readonly notify: Notify;
        readonly navigate: (path: string) => void;
        readonly state: AuthPageState;
    },
) {
    const { kind, t, notify, navigate, state } = options;
    const operations: AuthOperationsState = {
        kind, t, notify: (title, description, tone) => {
            notify(title, description, tone);
        }, navigate,
        code: state.code, setCode: state.setCode, factor: state.factor, payload: state.payload,
        email: state.email, setEmail: state.setEmail,
        emailMfaChallengeId: state.emailMfaChallengeId, setEmailMfaChallengeId: state.setEmailMfaChallengeId,
        emailSignInChallengeId: state.emailSignInChallengeId, setEmailSignInChallengeId: state.setEmailSignInChallengeId,
        otpPurpose: state.otpPurpose, setOtpPurpose: state.setOtpPurpose,
        registration: state.registration, setRegistration: state.setRegistration,
        registrationResendSeconds: state.registrationResendSeconds,
        setRegistrationResendSeconds: state.setRegistrationResendSeconds,
        passwordResetChallengeId: state.passwordResetChallengeId,
        setPasswordResetChallengeId: state.setPasswordResetChallengeId,
        emailMfaEnrollmentChallengeId: state.emailMfaEnrollmentChallengeId,
        setEmailMfaEnrollmentChallengeId: state.setEmailMfaEnrollmentChallengeId,
        setNotice: state.setNotice, setRequiredEnrollmentComplete: state.setRequiredEnrollmentComplete,
        setPayload: state.setPayload, setRecoveryCodes: state.setRecoveryCodes, setSessions: state.setSessions,
        setBusy: state.setBusy, busy: state.busy,
        clearErrors: () => { state.setFieldErrors({}); },
        applyFieldErrors: errors => {
            const nextErrors = localizedFieldErrors(errors, t);
            state.setFieldErrors(nextErrors);
            const firstInvalid = Object.keys(nextErrors)[0];
            if (firstInvalid) {
                window.requestAnimationFrame(() => {
                    document.querySelector<HTMLElement>(`[name="${CSS.escape(firstInvalid)}"]`)?.focus();
                });
            }
        },
    };

    return {
        submit: (event: SyntheticEvent<HTMLFormElement>) => { void submitAuthForm(event, operations); },
        resendRegistrationCode: () => { void resendRegistration(operations); },
        submitRecoveryCodes: (event: SyntheticEvent<HTMLFormElement>) => { void issueRecoveryCodes(event, operations); },
        enrollEmailMfa: (event: SyntheticEvent<HTMLFormElement>) => { void enrollEmailFactor(event, operations); },
        revokeSession: (sessionId: string) => { void revokeAuthSession(sessionId, operations); },
        clearRecoveryCodes: () => { state.setRecoveryCodes([]); },
    };
}
