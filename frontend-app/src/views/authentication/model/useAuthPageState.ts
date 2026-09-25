import { useState, type Dispatch, type SetStateAction } from "react";
import type { AuthPageKind } from "../../../features/authentication/model/AuthPageKind";
import type { AuthSession } from "../../../widgets/authentication-step/model/AuthStepProps";
import type { AuthOperationsState } from "./authOperations";

export interface AuthPageState {
    readonly busy: AuthOperationsState["busy"];
    readonly setBusy: AuthOperationsState["setBusy"];
    readonly fieldErrors: Record<string, string>;
    readonly setFieldErrors: (errors: Record<string, string>) => void;
    readonly notice: string;
    readonly setNotice: AuthOperationsState["setNotice"];
    readonly payload: string;
    readonly setPayload: AuthOperationsState["setPayload"];
    readonly code: string;
    readonly setCode: AuthOperationsState["setCode"];
    readonly factor: string;
    readonly setFactor: Dispatch<SetStateAction<string>>;
    readonly showPassword: boolean;
    readonly setShowPassword: (show: boolean) => void;
    readonly preview: AuthPageKind;
    readonly setPreview: (kind: AuthPageKind) => void;
    readonly googleEnabled: boolean;
    readonly setGoogleEnabled: (enabled: boolean) => void;
    readonly availableMethods: string[];
    readonly setAvailableMethods: (methods: string[]) => void;
    readonly registration: AuthOperationsState["registration"];
    readonly setRegistration: AuthOperationsState["setRegistration"];
    readonly registrationResendSeconds: number;
    readonly setRegistrationResendSeconds: AuthOperationsState["setRegistrationResendSeconds"];
    readonly email: string;
    readonly setEmail: AuthOperationsState["setEmail"];
    readonly emailSignInChallengeId: string;
    readonly setEmailSignInChallengeId: AuthOperationsState["setEmailSignInChallengeId"];
    readonly otpPurpose: string;
    readonly setOtpPurpose: AuthOperationsState["setOtpPurpose"];
    readonly passwordResetChallengeId: string;
    readonly setPasswordResetChallengeId: AuthOperationsState["setPasswordResetChallengeId"];
    readonly emailMfaChallengeId: string;
    readonly setEmailMfaChallengeId: AuthOperationsState["setEmailMfaChallengeId"];
    readonly qrCode: string;
    readonly setQrCode: (code: string) => void;
    readonly sessions: AuthSession[];
    readonly setSessions: AuthOperationsState["setSessions"];
}

export function useAuthPageState(): AuthPageState {
    const [busy, setBusy] = useState(false);
    const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({});
    const [notice, setNotice] = useState("");
    const [payload, setPayload] = useState("");
    const [code, setCode] = useState("");
    const [factor, setFactor] = useState("TOTP");
    const [showPassword, setShowPassword] = useState(false);
    const [preview, setPreview] = useState<AuthPageKind>("login");
    const [googleEnabled, setGoogleEnabled] = useState(false);
    const [availableMethods, setAvailableMethods] = useState<string[]>([]);
    const [registration, setRegistration] = useState<AuthOperationsState["registration"]>(null);
    const [registrationResendSeconds, setRegistrationResendSeconds] = useState(60);
    const [email, setEmail] = useState("");
    const [emailSignInChallengeId, setEmailSignInChallengeId] = useState("");
    const [otpPurpose, setOtpPurpose] = useState("");
    const [passwordResetChallengeId, setPasswordResetChallengeId] = useState("");
    const [emailMfaChallengeId, setEmailMfaChallengeId] = useState("");
    const [qrCode, setQrCode] = useState("");
    const [sessions, setSessions] = useState<AuthSession[]>([]);

    return {
        busy, setBusy, fieldErrors, setFieldErrors, notice, setNotice, payload, setPayload, code, setCode, factor, setFactor,
        showPassword, setShowPassword, preview, setPreview, googleEnabled, setGoogleEnabled,
        availableMethods, setAvailableMethods, registration, setRegistration, registrationResendSeconds,
        setRegistrationResendSeconds, email, setEmail, emailSignInChallengeId, setEmailSignInChallengeId,
        otpPurpose, setOtpPurpose, passwordResetChallengeId, setPasswordResetChallengeId,
        emailMfaChallengeId, setEmailMfaChallengeId, qrCode, setQrCode,
        sessions, setSessions,
    };
}
