import type { SyntheticEvent } from "react";
import type { AuthStringKey } from "../../../features/authentication/model/strings";

export type AuthSubmitEvent = SyntheticEvent<HTMLFormElement>;

export interface AuthSession {
    id: string;
    device: string;
    createdAt: string;
    lastUsedAt: string;
    revokedAt: string | null;
}

export interface AuthStepProps {
    busy: boolean;
    submit: (event: AuthSubmitEvent) => void;
    payload: string;
    qrCode: string;
    code: string;
    setCode: (value: string) => void;
    factor: string;
    setFactor: (value: string) => void;
    availableMethods: string[];
    emailMfaCodeRequested: boolean;
    showPassword: boolean;
    setShowPassword: (value: boolean) => void;
    email: string;
    setEmail: (value: string) => void;
    googleEnabled: boolean;
    registrationPending: boolean;
    registrationChallengeReady: boolean;
    registrationResendSeconds: number;
    resendRegistrationCode: () => void;
    emailCodeRequested: boolean;
    otpPurpose: string;
    passwordResetRequested: boolean;
    fieldErrors: Record<string, string>;
    clearFieldErrors: () => void;
    sessions: AuthSession[];
    revokeSession: (id: string) => void;
    t: (key: AuthStringKey, vars?: Record<string, string | number>) => string;
}
