import { useAuth } from "../model/AuthContext";

export function useAuthStepProps() {
    const { state, operations, t } = useAuth();

    return {
        busy: state.busy,
        submit: operations.submit,
        payload: state.payload,
        qrCode: state.qrCode,
        code: state.code,
        setCode: state.setCode,
        factor: state.factor,
        setFactor: (value: string) => {
            state.setFactor(value);
            state.setEmailMfaChallengeId("");
        },
        availableMethods: state.availableMethods,
        emailMfaCodeRequested: Boolean(state.emailMfaChallengeId),
        showPassword: state.showPassword,
        setShowPassword: state.setShowPassword,
        email: state.email,
        setEmail: state.setEmail,
        primaryMethods: state.primaryMethods,
        registrationPending: state.registration !== null,
        startGoogleSignIn: operations.startGoogleSignIn,
        registrationChallengeReady: Boolean(state.registration?.challengeId),
        registrationResendSeconds: state.registrationResendSeconds,
        resendRegistrationCode: operations.resendRegistrationCode,
        emailCodeRequested: Boolean(state.emailSignInChallengeId),
        otpPurpose: state.otpPurpose,
        fieldErrors: state.fieldErrors,
        clearFieldErrors: () => {
            state.setFieldErrors({});
        },
        sessions: state.sessions,
        revokeSession: operations.revokeSession,
        t,
    };
}