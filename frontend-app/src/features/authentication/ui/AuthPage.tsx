"use client";

import type { FormEvent, ReactElement } from "react";
import { cloneElement, useEffect, useState } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import QRCode from "qrcode";
import { Button } from "frontend-shared/ui/button";
import { Field } from "frontend-shared/ui/field";
import type { InputProps } from "frontend-shared/ui/input";
import { Input } from "frontend-shared/ui/input";
import { authClient, AuthError } from "../api/client";
import { useToast } from "frontend-shared/ui/toast";
import { AuthLayout } from "./AuthLayout";
import styles from "./auth.module.css";
import type { AuthStringKey } from "../model/strings";
import { useAuthStrings } from "../model/strings";

export type AuthPageKind =
    "login"
    | "register"
    | "mfa"
    | "enrollment"
    | "forgot"
    | "otp"
    | "google"
    | "callback"
    | "security"
    | "preview";
const headings = {
    login: ["loginTitle", "loginDescription"], register: ["registerTitle", "registerDescription"],
    mfa: ["mfaTitle", "mfaTitleHelp"], enrollment: ["enrollTitle", "enrollmentDescription"],
    forgot: ["forgotTitle", "forgotHelp"], otp: ["verifyTitle", "otpHelp"],
    google: ["google", "googleAvailableHelp"], callback: ["oauthTitle", "callbackDescription"],
    security: ["security", "securityDescription"],
    preview: ["demo", "previewDescription"],
} as const satisfies Record<AuthPageKind, readonly [AuthStringKey, AuthStringKey]>;

function message(error: unknown, t: (key: AuthStringKey) => string): string {
    return error instanceof AuthError ? error.message : t("requestFailed");
}

export function AuthPage({ kind }: { kind: AuthPageKind }) {
    const router = useRouter();
    const t = useAuthStrings("auth");
    const { actions: toast } = useToast();
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
    const [registration, setRegistration] = useState<{
        userId: string;
        challengeId: string | null;
        email: string
    } | null>(null);
    const [registrationResendSeconds, setRegistrationResendSeconds] = useState(60);
    const [email, setEmail] = useState("");
    const [emailSignInChallengeId, setEmailSignInChallengeId] = useState("");
    const [otpPurpose, setOtpPurpose] = useState("");
    const [passwordResetChallengeId, setPasswordResetChallengeId] = useState("");
    const [emailMfaChallengeId, setEmailMfaChallengeId] = useState("");
    const [recoveryCodes, setRecoveryCodes] = useState<string[]>([]);
    const [qrCode, setQrCode] = useState("");
    const [sessions, setSessions] = useState<Array<{
        id: string;
        device: string;
        createdAt: string;
        lastUsedAt: string;
        revokedAt: string | null
    }>>([]);

    useEffect(() => {
        if (kind === "google") {
            const result = new URLSearchParams(window.location.search).get("error");
            if (result === "cancelled") {
                toast.add({ title: t("googleCancelled"), tone: "attention" });
            } else if (result) {
                toast.add({ title: t("googleFailed"), description: t("alternateSignInHelp"), tone: "danger" });
            }
        }
        if (kind === "login" || kind === "register" || kind === "google") {
            void fetch("/api/v1/auth/providers", { credentials: "same-origin", cache: "no-store" })
                .then(response => response.ok ? response.json() as Promise<{ google?: boolean }> : null)
                .then(providers => setGoogleEnabled(providers?.google === true))
                .catch(() => setGoogleEnabled(false));
        }
        if (kind === "security") {
            void authClient.get<Array<{
                id: string;
                device: string;
                createdAt: string;
                lastUsedAt: string;
                revokedAt: string | null
            }>>("/sessions")
                .then(setSessions)
                .catch(reason => toast.add({
                    title: t("securityLoadFailed"),
                    description: message(reason, t),
                    tone: "danger"
                }));
        }
        if (kind === "mfa") {
            setEmailMfaChallengeId("");
            const pendingId = new URLSearchParams(window.location.search).get("pending_id");
            if (pendingId) sessionStorage.setItem("tallyvane.pendingId", pendingId);
            const queryMethods = new URLSearchParams(window.location.search).get("methods");
            const storedMethods = sessionStorage.getItem("tallyvane.availableMethods");
            const methods = queryMethods?.split(",").filter(Boolean) ?? JSON.parse(storedMethods ?? "[]") as string[];
            setAvailableMethods(methods);
            if (methods.length > 0 && !methods.includes(factor)) setFactor(methods[0] ?? "TOTP");
        }
        if (kind === "otp" && new URLSearchParams(window.location.search).get("purpose") === "registration") {
            setOtpPurpose("registration");
            try {
                const saved = sessionStorage.getItem("tallyvane.registration");
                if (saved) {
                    const parsed = JSON.parse(saved) as { userId: string; challengeId: string | null; email: string };
                    setRegistration(parsed);
                    setEmail(parsed.email);
                }
            } catch {
                sessionStorage.removeItem("tallyvane.registration");
            }
        }
        if (kind === "otp" && new URLSearchParams(window.location.search).get("purpose") === "login") {
            setOtpPurpose("login");
            setEmailSignInChallengeId("");
        }
    }, [kind]);

    useEffect(() => {
        if (kind !== "otp" || otpPurpose !== "registration" || registrationResendSeconds <= 0) return;
        const timer = window.setTimeout(() => setRegistrationResendSeconds(seconds => Math.max(0, seconds - 1)), 1000);
        return () => window.clearTimeout(timer);
    }, [kind, otpPurpose, registrationResendSeconds]);

    useEffect(() => {
        if (!payload) {
            setQrCode("");
            return;
        }
        let active = true;
        void QRCode.toDataURL(payload, { width: 240, margin: 1, errorCorrectionLevel: "M" })
            .then(image => { if (active) setQrCode(image); })
            .catch(() => { if (active) setQrCode(""); });
        return () => { active = false; };
    }, [payload]);

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setBusy(true);
        setFieldErrors({});
        setNotice("");
        const form = new FormData(event.currentTarget);
        try {
            if (kind === "login") {
                const result = await authClient.post<{
                    status: string;
                    pendingId?: string;
                    availableMethods?: string[]
                }>("/login/password", {
                    email: String(form.get("email") ?? ""),
                    password: String(form.get("password") ?? ""),
                    device: "Browser",
                });
                if (result.status === "issued") router.push("/today");
                else if (result.status === "requires_second_factor" && result.pendingId) {
                    sessionStorage.setItem("tallyvane.pendingId", result.pendingId);
                    sessionStorage.setItem("tallyvane.availableMethods", JSON.stringify(result.availableMethods ?? []));
                    router.push("/mfa");
                } else toast.add({
                    title: t("signInContinuedError"),
                    description: t("unknownAuthStep"),
                    tone: "danger"
                });
            } else if (kind === "register") {
                const result = await authClient.post<{
                    userId: string;
                    challengeId: string | null
                }>("/register/password", {
                    email: String(form.get("email") ?? ""), password: String(form.get("password") ?? ""),
                    displayName: String(form.get("name") ?? "") || null,
                });
                const registration = {
                    userId: result.userId,
                    challengeId: result.challengeId,
                    email: String(form.get("email") ?? "")
                };
                sessionStorage.setItem("tallyvane.registration", JSON.stringify(registration));
                setRegistration(registration);
                setEmail(registration.email);
                setOtpPurpose("registration");
                setRegistrationResendSeconds(60);
                if (!result.challengeId) toast.add({
                    title: t("accountCreated"),
                    description: t("delayedMail"),
                    tone: "attention"
                });
                router.push("/otp?purpose=registration");
            } else if (kind === "mfa") {
                const pendingId = sessionStorage.getItem("tallyvane.pendingId");
                if (!pendingId) throw new Error(t("signInExpired"));
                if (factor === "EMAIL_OTP" && !emailMfaChallengeId) {
                    const requested = await authClient.post<{ challengeId: string }>("/mfa/email/request", { pendingId });
                    setEmailMfaChallengeId(requested.challengeId);
                    toast.add({ title: t("mfaEmailCodeSent"), description: t("mfaEmailCodeDescription"), tone: "success" });
                } else {
                    const result = await authClient.post<{ status: string }>("/mfa/verify", {
                        pendingId,
                        kind: factor,
                        code,
                        ...(factor === "EMAIL_OTP" ? { challengeId: emailMfaChallengeId } : {}),
                    });
                    if (result.status === "issued") {
                        sessionStorage.removeItem("tallyvane.pendingId");
                        router.push("/today");
                    }
                }
            } else if (kind === "enrollment") {
                if (!payload) {
                    const result = await authClient.post<{ otpauthUri: string }>("/mfa/enroll", { kind: "TOTP" });
                    setPayload(result.otpauthUri);
                } else {
                    await authClient.post("/mfa/confirm", { kind: "TOTP", code });
                    toast.add({
                        title: t("authenticatorEnabled"),
                        description: t("authenticatorEnabledDescription"),
                        tone: "success"
                    });
                    setNotice(t("authenticatorConfirmed"));
                    setPayload("");
                    setCode("");
                }
            } else if (kind === "otp") {
                const purpose = new URLSearchParams(window.location.search).get("purpose");
                if (purpose === "login") {
                    if (!emailSignInChallengeId) {
                        const issued = await authClient.post<{ challengeId: string }>("/login/email/code", { email });
                        setEmailSignInChallengeId(issued.challengeId);
                        setCode("");
                        toast.add({
                            title: t("signInCodeSent"),
                            description: t("checkInboxForCode", { email }),
                            tone: "success"
                        });
                    } else {
                        const result = await authClient.post<{
                            status: string;
                            pendingId?: string;
                            availableMethods?: string[]
                        }>("/login/email/verify", {
                            challengeId: emailSignInChallengeId, email, code, device: "Browser",
                        });
                        if (result.status === "issued") router.push("/today");
                        else if (result.status === "requires_second_factor" && result.pendingId) {
                            sessionStorage.setItem("tallyvane.pendingId", result.pendingId);
                            sessionStorage.setItem("tallyvane.availableMethods", JSON.stringify(result.availableMethods ?? []));
                            router.push("/mfa");
                        } else if (result.status === "requires_enrollment" && result.pendingId) {
                            sessionStorage.setItem("tallyvane.pendingId", result.pendingId);
                            router.push("/mfa-enrollment");
                        } else toast.add({
                            title: t("signInContinuedError"),
                            description: t("unknownAuthStep"),
                            tone: "danger"
                        });
                    }
                } else {
                    const pending = registration ?? JSON.parse(sessionStorage.getItem("tallyvane.registration") ?? "null") as {
                        userId: string;
                        challengeId: string | null;
                        email: string
                    } | null;
                    if (!pending) throw new Error(t("missing"));
                    if (!pending.challengeId) throw new Error(t("requestNewCodeFirst"));
                    await authClient.post("/register/email/verify", { ...pending, code });
                    toast.add({
                        title: t("emailVerified"),
                        description: t("emailVerifiedDescription"),
                        tone: "success"
                    });
                    sessionStorage.removeItem("tallyvane.registration");
                    setRegistration(null);
                    setNotice(t("emailVerifiedNotice"));
                }
            } else if (kind === "forgot") {
                if (!passwordResetChallengeId) {
                    const issued = await authClient.post<{ challengeId: string }>("/password/forgot", { email });
                    setPasswordResetChallengeId(issued.challengeId);
                    setCode("");
                    toast.add({
                        title: t("resetCodeSent"),
                        description: t("checkInboxForCode", { email }),
                        tone: "success"
                    });
                } else {
                    await authClient.post("/password/reset", {
                        challengeId: passwordResetChallengeId,
                        email,
                        code,
                        newPassword: String(form.get("newPassword") ?? ""),
                    });
                    setPasswordResetChallengeId("");
                    setNotice(t("passwordUpdatedNotice"));
                    toast.add({
                        title: t("passwordUpdated"),
                        description: t("passwordUpdatedDescription"),
                        tone: "success"
                    });
                }
            } else if (kind === "security") {
                await authClient.post("/account/password", {
                    currentPassword: String(form.get("currentPassword") ?? ""),
                    newPassword: String(form.get("newPassword") ?? ""),
                });
                setNotice(t("passwordChanged"));
                toast.add({ title: t("passwordChanged"), tone: "success" });
            } else {
                setNotice(t("previewActionNotice"));
            }
        } catch (reason) {
            if (reason instanceof AuthError && Object.keys(reason.fieldErrors).length) {
                const nextErrors = { ...reason.fieldErrors };
                setFieldErrors(nextErrors);
                const firstInvalid = Object.keys(nextErrors)[0];
                if (firstInvalid) window.requestAnimationFrame(() => document.querySelector<HTMLElement>(`[name="${ CSS.escape(firstInvalid) }"]`)?.focus());
            } else {
                const detail = message(reason, t);
                toast.add({ title: t("authFailed"), description: detail, tone: "danger" });
            }
        } finally {
            setBusy(false);
        }
    }

    async function resendRegistrationCode() {
        const pending = registration ?? JSON.parse(sessionStorage.getItem("tallyvane.registration") ?? "null") as {
            userId: string;
            challengeId: string | null;
            email: string
        } | null;
        if (!pending || registrationResendSeconds > 0 || busy) return;
        setBusy(true);
        try {
            const result = await authClient.post<{
                challengeId: string | null
            }>("/register/email/resend", { userId: pending.userId, email: pending.email });
            if (result.challengeId) {
                const updated = { ...pending, challengeId: result.challengeId };
                setRegistration(updated);
                sessionStorage.setItem("tallyvane.registration", JSON.stringify(updated));
                setCode("");
            }
            setRegistrationResendSeconds(60);
            toast.add({ title: t("resendNeutralTitle"), description: t("resendNeutralDescription"), tone: "success" });
        } catch (reason) {
            toast.add({ title: t("resendFailed"), description: message(reason, t), tone: "danger" });
        } finally {
            setBusy(false);
        }
    }

    async function issueRecoveryCodes(event: FormEvent<HTMLFormElement>) {
        event.preventDefault();
        setBusy(true);
        setFieldErrors({});
        setNotice("");
        const target = event.currentTarget;
        const form = new FormData(target);
        try {
            const result = await authClient.post<{ codes: string[] }>("/mfa/backup-codes", {
                currentPassword: String(form.get("backupCurrentPassword") ?? ""),
            });
            setRecoveryCodes(result.codes);
            setNotice(t("recoveryCodesIssued"));
            toast.add({ title: t("recoveryCodesIssued"), description: t("recoveryCodesOneTime"), tone: "success" });
            target.reset();
        } catch (reason) {
            if (reason instanceof AuthError && Object.keys(reason.fieldErrors).length) {
                setFieldErrors(reason.fieldErrors);
            } else {
                toast.add({ title: t("authFailed"), description: message(reason, t), tone: "danger" });
            }
        } finally {
            setBusy(false);
        }
    }

    async function revokeSession(sessionId: string) {
        try {
            await authClient.remove<void>(`/sessions/${ encodeURIComponent(sessionId) }`);
            setSessions(current => current.filter(session => session.id !== sessionId));
            toast.add({ title: t("sessionRevoked"), tone: "success" });
        } catch (reason) {
            toast.add({ title: t("sessionRevokeFailed"), description: message(reason, t), tone: "danger" });
        }
    }

    const [titleKey, descriptionKey] = headings[kind];
    const [title, description] = [t(titleKey), t(descriptionKey)];
    const previewOnly = kind === "preview";
    return <AuthLayout><h1 className={ styles.heading }>{ previewOnly ? t(headings[preview][0]) : title }</h1>
        <p className={ styles.description }>{ previewOnly ? t(headings[preview][1]) : description }</p>
        { previewOnly && <nav className={ styles.catalog } aria-label={ t("demo") }>
            <label htmlFor="auth-preview-page">{ t("previewScreen") }</label>
            <select id="auth-preview-page" value={ preview } onChange={ event => {
                setPreview(event.target.value as AuthPageKind);
                setFieldErrors({});
                setNotice("");
            } }>
                { (["login", "register", "mfa", "enrollment", "forgot", "otp", "google", "callback", "security"] as AuthPageKind[]).map(page =>
                    <option key={ page } value={ page }>{ t(headings[page][0]) }</option>) }
            </select>
        </nav> }
        { notice && <p role="status" className={ styles.notice }>{ notice }</p> }
        { renderPage(previewOnly ? preview : kind, {
            busy,
            submit,
            payload,
            qrCode,
            code,
            setCode,
            factor,
            setFactor,
            availableMethods,
            emailMfaCodeRequested: Boolean(emailMfaChallengeId),
            showPassword,
            setShowPassword,
            email,
            setEmail,
            googleEnabled,
            registrationPending: registration !== null,
            registrationChallengeReady: Boolean(registration?.challengeId),
            registrationResendSeconds,
            resendRegistrationCode,
            emailCodeRequested: Boolean(emailSignInChallengeId),
            otpPurpose,
            passwordResetRequested: Boolean(passwordResetChallengeId),
            fieldErrors,
            recoveryCodes,
            issueRecoveryCodes,
            clearRecoveryCodes: () => setRecoveryCodes([]),
            sessions,
            revokeSession,
            t
        }) }
        <p className={ styles.bottom }>
            { kind === "login" && <>{ t("noAccountPrompt") } <Link className={ styles.link }
                                                                   href="/register">{ t("createAccountLink") }</Link></> }
            { kind === "register" && <>{ t("hasAccountPrompt") } <Link className={ styles.link }
                                                                       href="/login">{ t("signInLink") }</Link></> }
            { kind === "login" && <span className="block"><Link className={ styles.link }
                                                                href="/forgot-password">{ t("forgotPasswordLink") }</Link></span> }
            { kind === "login" && <span className="block"><Link className={ styles.link }
                                                                href="/otp?purpose=login">{ t("signInEmailCodeLink") }</Link></span> }
            { !previewOnly &&
                <span className="block"><Link className={ styles.link } href="/auth-preview">{ t("openPreview") }</Link></span> }
        </p>
    </AuthLayout>;
}

type PageProps = {
    busy: boolean;
    submit: (event: FormEvent<HTMLFormElement>) => void;
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
    recoveryCodes: string[];
    issueRecoveryCodes: (event: FormEvent<HTMLFormElement>) => void;
    clearRecoveryCodes: () => void;
    sessions: Array<{ id: string; device: string; createdAt: string; lastUsedAt: string; revokedAt: string | null }>;
    revokeSession: (id: string) => void;
    t: (key: AuthStringKey, vars?: Record<string, string | number>) => string
};

function renderPage(kind: AuthPageKind, props: PageProps) {
    const { busy, submit, payload, qrCode, code, setCode, factor, setFactor, showPassword, setShowPassword, t } = props;
    if (kind === "login" || kind === "register") {
        return <>
            { props.googleEnabled ? <a className={ styles.provider } href="/api/v1/auth/google/oauth/start"><span
                    className={ styles.methodIcon } aria-hidden="true">G</span>{ t("continueWithGoogle") }</a> :
                <button className={ styles.provider } type="button" disabled><span className={ styles.methodIcon }
                                                                                   aria-hidden="true">G</span>{ t("continueWithGoogle") }
                    <span className={ styles.hint }>{ t("notConfigured") }</span></button> }
            { kind === "register" ? <details className={ styles.emailDisclosure }>
                <summary className={ styles.disclosureSummary }><span
                    className={ styles.disclosureTitle }>{ t("emailDisclosure") }</span><span
                    className={ styles.hint }>{ t("emailDisclosureHelp") }</span></summary>
                <form className={ styles.form } onSubmit={ submit }>
                    { field("name", t("name"), <Input autoComplete="name"/>, props.fieldErrors) }
                    { field("email", t("email"), <Input type="email" autoComplete="email"
                                                        required/>, props.fieldErrors) }
                    { field("password", t("password"), <Input type={ showPassword ? "text" : "password" }
                                                              autoComplete="new-password" minLength={ 15 }
                                                              maxLength={ 128 }
                                                              required/>, props.fieldErrors, t("passwordHelp")) }
                    <Button tone="ghost" size="sm" className={ styles.quiet ?? "" } type="button"
                            aria-pressed={ showPassword }
                            onClick={ () => setShowPassword(!showPassword) }>{ showPassword ? t("hidePassword") : t("showPassword") }</Button>
                    <Button tone="primary" className={ styles.full ?? "" } type="submit"
                            loading={ busy }>{ t("createAccountAction") }</Button>
                </form>
            </details> : <>
                <div className={ styles.divider }>{ t("divider") }</div>
                <form className={ styles.form } onSubmit={ submit }>
                    { field("email", t("email"), <Input type="email" autoComplete="email"
                                                        required/>, props.fieldErrors) }
                    { field("password", t("password"), <Input type={ showPassword ? "text" : "password" }
                                                              autoComplete="current-password" minLength={ 15 }
                                                              maxLength={ 128 } required/>, props.fieldErrors) }
                    <Button tone="ghost" size="sm" className={ styles.quiet ?? "" } type="button"
                            aria-pressed={ showPassword }
                            onClick={ () => setShowPassword(!showPassword) }>{ showPassword ? t("hidePassword") : t("showPassword") }</Button>
                    <Button tone="primary" className={ styles.full ?? "" } type="submit"
                            loading={ busy }>{ t("signIn") }</Button>
                </form>
            </> }
            <p className={ styles.hint }>{ t("emailCodePreviewHelp") }</p>
        </>;
    }
    if (kind === "mfa") {
        return <form className={ styles.form } onSubmit={ submit }>
            <div className={ styles.field }><label className={ styles.fieldLabel }
                                                   htmlFor="auth-factor">{ t("verificationMethod") }</label><select
                id="auth-factor" name="factor" aria-invalid={ Boolean(props.fieldErrors.factor) }
                aria-describedby={ props.fieldErrors.factor ? "auth-factor-error" : undefined } value={ factor }
                onChange={ event => setFactor(event.target.value) }>{ (props.availableMethods.length ? props.availableMethods : ["TOTP"]).map(method =>
                <option key={ method }
                        value={ method }>{ method === "TOTP" ? t("methodTotp") : method === "BACKUP_CODE" ? t("backupCodeLabel") : t("emailCodeLabel") }</option>) }</select>{ props.fieldErrors.factor &&
                <span id="auth-factor-error" className={ styles.fieldError }>{ props.fieldErrors.factor }</span> }</div>
            { (factor !== "EMAIL_OTP" || props.emailMfaCodeRequested) && field("code", factor === "TOTP" ? t("authenticatorCode") : factor === "EMAIL_OTP" ? t("code") : t("backupCodeLabel"), <Input
                className={ styles.codeInput ?? "" } value={ code } onChange={ event => setCode(event.target.value) }
                autoComplete="one-time-code" inputMode="numeric" required/>, props.fieldErrors) }
            <Button tone="primary" className={ styles.full ?? "" } type="submit"
                    loading={ busy }>{ factor === "EMAIL_OTP" && !props.emailMfaCodeRequested ? t("sendEmailMfaCode") : t("verifyAndContinue") }</Button>
        </form>;
    }
    if (kind === "enrollment") {
        return <form className={ styles.form } onSubmit={ submit }>
            { payload && <>
                { qrCode ? <img className={ styles.qr } src={ qrCode } alt={ t("authenticatorQr") } /> :
                    <p role="status">{ t("qrUnavailable") }</p> }
                <p className={ styles.hint }>{ t("manualSetupKey") }</p>
                <code className={ styles.secret }>{ new URL(payload).searchParams.get("secret") ?? payload }</code>
            </> }
            { field("code", t("codeFromAuthenticator"), <Input className={ styles.codeInput ?? "" } value={ code }
                                                               onChange={ event => setCode(event.target.value) }
                                                               inputMode="numeric"
                                                               autoComplete="one-time-code"/>, props.fieldErrors) }
            <Button tone="primary" className={ styles.full ?? "" } type="submit"
                    loading={ busy }>{ payload ? t("confirmAuthenticator") : t("enrollStart") }</Button>
        </form>;
    }
    if (kind === "preview") {
        return <section className={ styles.notice }>
            <strong>{ t("previewOnly") }</strong> { t("previewCatalogHelp") }</section>;
    }
    if (kind === "google") {
        return props.googleEnabled
            ? <a className={ `${ styles.provider } ${ styles.full }` }
                 href="/api/v1/auth/google/oauth/start">{ t("continueWithGoogle") }</a>
            : <p className={ styles.hint }>{ t("googleNotConfigured") }</p>;
    }
    if (kind === "security") {
        return <>
            <form className={ styles.form } onSubmit={ submit }>
                { field("currentPassword", t("currentPassword"), <Input type="password" autoComplete="current-password"
                                                                        required/>, props.fieldErrors) }
                { field("newPassword", t("newPassword"), <Input type="password" autoComplete="new-password"
                                                                minLength={ 15 }
                                                                maxLength={ 128 }
                                                                required/>, props.fieldErrors, t("passwordHelp")) }
                <Button tone="primary" className={ styles.full ?? "" } type="submit"
                        loading={ busy }>{ t("changePasswordAction") }</Button>
            </form>
            <section className={ styles.form } aria-labelledby="recovery-codes-heading">
                <h2 id="recovery-codes-heading">{ t("recoveryCodesTitle") }</h2>
                <p className={ styles.hint }>{ t("backupWarning") }</p>
                <form onSubmit={ props.issueRecoveryCodes }>
                    { field("backupCurrentPassword", t("confirmPassword"), <Input type="password"
                                                                                  autoComplete="current-password"
                                                                                  required/>, props.fieldErrors) }
                    <Button tone="primary" className={ styles.full ?? "" } type="submit"
                            loading={ busy }>{ t("backupGenerate") }</Button>
                </form>
                { props.recoveryCodes.length > 0 && <div role="status">
                    <p className={ styles.hint }>{ t("recoveryCodesOneTime") }</p>
                    <ul aria-label={ t("recoveryCodesTitle") }>{ props.recoveryCodes.map(code => <li key={ code }>
                        <code>{ code }</code></li>) }</ul>
                    <Button tone="ghost" size="sm" type="button"
                            onClick={ props.clearRecoveryCodes }>{ t("backupCodesSaved") }</Button>
                </div> }
            </section>
            <section className={ styles.form } aria-labelledby="sessions-heading">
                <h2 id="sessions-heading">{ t("sessions") }</h2>
                { props.sessions.filter(session => !session.revokedAt).length === 0
                    ? <p className={ styles.hint }>{ t("noSessions") }</p>
                    : <ul>{ props.sessions.filter(session => !session.revokedAt).map(session => <li key={ session.id }>
                        <span>{ session.device }</span>
                        <span className={ styles.hint }>{ t("lastUsed", {
                            date: new Intl.DateTimeFormat(undefined, {
                                dateStyle: "medium",
                                timeStyle: "short"
                            }).format(new Date(session.lastUsedAt))
                        }) }</span>
                        <Button tone="ghost" size="sm" type="button"
                                onClick={ () => props.revokeSession(session.id) }>{ t("revoke") }</Button>
                    </li>) }</ul> }
            </section>
        </>;
    }
    if (kind === "otp" && props.registrationPending) {
        return <form className={ styles.form } onSubmit={ submit }>
            <p className={ styles.hint }>{ t("registrationCodeSent", { email: props.email }) }</p>
            { field("code", t("code"), <Input className={ styles.codeInput ?? "" } value={ code }
                                              onChange={ event => setCode(event.target.value) } inputMode="numeric"
                                              autoComplete="one-time-code" maxLength={ 6 }
                                              required/>, props.fieldErrors) }
            <Button tone="primary" className={ styles.full ?? "" } type="submit" loading={ busy }
                    disabled={ !props.registrationChallengeReady }>{ t("verifyEmail") }</Button>
            <Button tone="ghost" size="sm" type="button" onClick={ props.resendRegistrationCode }
                    disabled={ busy || props.registrationResendSeconds > 0 }>
                { props.registrationResendSeconds > 0 ? t("resendCodeIn", { seconds: props.registrationResendSeconds }) : t("resendVerificationCode") }
            </Button>
        </form>;
    }
    if (kind === "otp" && !props.otpPurpose) {
        return <p className={ styles.hint }
                  role="status">{ t("preparingVerification") }</p>;
    }
    if (kind === "otp" && props.otpPurpose === "login") {
        return <form className={ styles.form } onSubmit={ submit }>
            { field("email", t("email"), <Input type="email" value={ props.email }
                                                onChange={ event => props.setEmail(event.target.value) }
                                                autoComplete="email" required
                                                disabled={ props.emailCodeRequested }/>, props.fieldErrors) }
            { props.emailCodeRequested && <>
                <p className={ styles.hint }>{ t("registrationCodeSent", { email: props.email }) }</p>
                { field("code", t("code"), <Input className={ styles.codeInput ?? "" } value={ code }
                                                  onChange={ event => setCode(event.target.value) } inputMode="numeric"
                                                  autoComplete="one-time-code" maxLength={ 6 }
                                                  required/>, props.fieldErrors) }
            </> }
            <Button tone="primary" className={ styles.full ?? "" } type="submit"
                    loading={ busy }>{ props.emailCodeRequested ? t("verifyAndContinue") : t("emailCode") }</Button>
            { props.emailCodeRequested && <Button tone="ghost" size="sm" type="button"
                                                  onClick={ () => window.location.reload() }>{ t("usePassword") }</Button> }
        </form>;
    }
    return <form className={ styles.form } onSubmit={ submit }>
        { (kind === "forgot" || kind === "otp") && field("email", t("email"), <Input type="email" value={ props.email }
                                                                                     onChange={ event => props.setEmail(event.target.value) }
                                                                                     autoComplete="email" required
                                                                                     disabled={ kind === "forgot" && props.passwordResetRequested }/>, props.fieldErrors) }
        { kind === "forgot" && props.passwordResetRequested && <>
            <p className={ styles.hint }>{ t("resetCodeNeutral") }</p>
            { field("code", t("code"), <Input className={ styles.codeInput ?? "" } value={ code }
                                              onChange={ event => setCode(event.target.value) } inputMode="numeric"
                                              autoComplete="one-time-code" maxLength={ 6 }
                                              required/>, props.fieldErrors) }
            { field("newPassword", t("newPassword"), <Input type="password" autoComplete="new-password" minLength={ 15 }
                                                            maxLength={ 128 }
                                                            required/>, props.fieldErrors, t("passwordHelp")) }
        </> }
        { kind === "callback" && <p role="status">{ t("callbackChecking") }</p> }
        <Button tone="primary" className={ styles.full ?? "" } type="submit"
                loading={ busy }>{ kind === "forgot" ? props.passwordResetRequested ? t("resetPassword") : t("sendResetCode") : kind === "otp" ? t("emailCode") : kind === "callback" ? t("returnToSignIn") : t("genericContinue") }</Button>
        { (kind !== "forgot" && kind !== "otp") && <p className={ styles.hint }>{ t("previewFlowHelp") }</p> }
    </form>;
}


function field(name: string, label: string, control: ReactElement<InputProps>, errors: Record<string, string>, help?: string) {
    const error = errors[name];
    const id = `auth-${ name }`;
    const child = cloneElement(control, { name, id });
    return <Field label={ label } { ...(help ? { description: help } : {}) } { ...(error ? { error } : {}) }
                  required={ Boolean(control.props.required) }>{ child }</Field>;
}
