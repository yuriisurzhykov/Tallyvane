"use client";

import { useEffect, useState } from "react";
import { cloneElement } from "react";
import type { FormEvent, ReactElement } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { Button } from "frontend-shared/ui/button";
import { Field } from "frontend-shared/ui/field";
import { Input } from "frontend-shared/ui/input";
import type { InputProps } from "frontend-shared/ui/input";
import { AuthError, authClient } from "../api/client";
import { useToast } from "frontend-shared/ui/toast";
import { AuthLayout } from "./AuthLayout";
import styles from "./auth.module.css";

export type AuthPageKind = "login" | "register" | "mfa" | "enrollment" | "forgot" | "otp" | "google" | "callback" | "preview";
const headings: Record<AuthPageKind, [string, string]> = {
    login: ["Welcome back.", "Pick up where your search left off."],
    register: ["Your next chapter starts here.", "Create a space for your job search."],
    mfa: ["One more check.", "Use one of the verification methods available to your account."],
    enrollment: ["Add an authenticator.", "Scan this code with an authenticator app, then confirm the current code."],
    forgot: ["Let’s get you back in.", "Password recovery is available when email challenges are configured on this server."],
    otp: ["Check your inbox.", "Email sign-in and verification codes are available when email challenges are configured."],
    google: ["Continue with Google", "Google sign-in becomes available after the server OAuth client is configured."],
    callback: ["Connecting with Google…", "This page will show the result of a configured OAuth callback."],
    preview: ["Authentication preview", "Explore the connected page layouts and interaction states."],
};

function message(error: unknown): string {
    return error instanceof AuthError ? error.message : "We couldn’t complete that request. Please try again.";
}

export function AuthPage({ kind }: { kind: AuthPageKind }) {
    const router = useRouter();
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
    const [registration, setRegistration] = useState<{ userId: string; challengeId: string; email: string } | null>(null);
    const [email, setEmail] = useState("");
    const [emailSignInChallengeId, setEmailSignInChallengeId] = useState("");
    const [otpPurpose, setOtpPurpose] = useState("");
    const [passwordResetChallengeId, setPasswordResetChallengeId] = useState("");

    useEffect(() => {
        if (kind === "google") {
            const result = new URLSearchParams(window.location.search).get("error");
            if (result === "cancelled") {
                toast.add({ title: "Google sign-in cancelled", tone: "attention" });
            } else if (result) {
                toast.add({ title: "Google sign-in failed", description: "Check your account or try another sign-in method.", tone: "danger" });
            }
        }
        if (kind === "login" || kind === "register" || kind === "google") {
            void fetch("/api/v1/auth/providers", { credentials: "same-origin", cache: "no-store" })
                .then(response => response.ok ? response.json() as Promise<{ google?: boolean }> : null)
                .then(providers => setGoogleEnabled(providers?.google === true))
                .catch(() => setGoogleEnabled(false));
        }
        if (kind === "mfa") {
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
                    const parsed = JSON.parse(saved) as { userId: string; challengeId: string; email: string };
                    setRegistration(parsed); setEmail(parsed.email);
                }
            } catch { sessionStorage.removeItem("tallyvane.registration"); }
        }
        if (kind === "otp" && new URLSearchParams(window.location.search).get("purpose") === "login") {
            setOtpPurpose("login");
            setEmailSignInChallengeId("");
        }
    }, [kind]);

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault(); setBusy(true); setFieldErrors({}); setNotice("");
        const form = new FormData(event.currentTarget);
        try {
            if (kind === "login") {
                const result = await authClient.post<{ status: string; pendingId?: string; availableMethods?: string[] }>("/login/password", {
                    email: String(form.get("email") ?? ""), password: String(form.get("password") ?? ""), device: "Browser",
                });
                if (result.status === "issued") router.push("/today");
                else if (result.status === "requires_second_factor" && result.pendingId) {
                    sessionStorage.setItem("tallyvane.pendingId", result.pendingId);
                    sessionStorage.setItem("tallyvane.availableMethods", JSON.stringify(result.availableMethods ?? []));
                    router.push("/mfa");
                } else toast.add({ title: "Sign-in could not continue", description: "The server returned an unrecognized authentication step.", tone: "danger" });
            } else if (kind === "register") {
                const result = await authClient.post<{ userId: string; challengeId: string | null }>("/register/password", {
                    email: String(form.get("email") ?? ""), password: String(form.get("password") ?? ""),
                    displayName: String(form.get("name") ?? "") || null,
                });
                if (!result.challengeId) throw new Error("Your account was created, but a verification code could not be issued. Contact the site owner before signing in.");
                const registration = { userId: result.userId, challengeId: result.challengeId, email: String(form.get("email") ?? "") };
                sessionStorage.setItem("tallyvane.registration", JSON.stringify(registration));
                router.push("/otp?purpose=registration");
            } else if (kind === "mfa") {
                const pendingId = sessionStorage.getItem("tallyvane.pendingId");
                if (!pendingId) throw new Error("Your sign-in check has expired. Start again to sign in.");
                const result = await authClient.post<{ status: string }>("/mfa/verify", { pendingId, kind: factor, code });
                if (result.status === "issued") { sessionStorage.removeItem("tallyvane.pendingId"); router.push("/today"); }
            } else if (kind === "enrollment") {
                if (!payload) {
                    const result = await authClient.post<{ otpauthUri: string }>("/mfa/enroll", { kind: "TOTP" });
                    setPayload(result.otpauthUri);
                } else {
                    await authClient.post("/mfa/confirm", { kind: "TOTP", code });
                    toast.add({ title: "Authenticator enabled", description: "Your account is now protected with an authenticator app.", tone: "success" });
                    setNotice("Authenticator confirmed. Your account is protected."); setPayload(""); setCode("");
                }
            } else if (kind === "otp") {
                const purpose = new URLSearchParams(window.location.search).get("purpose");
                if (purpose === "login") {
                    if (!emailSignInChallengeId) {
                        const issued = await authClient.post<{ challengeId: string }>("/login/email/code", { email });
                        setEmailSignInChallengeId(issued.challengeId);
                        setCode("");
                        toast.add({ title: "Sign-in code sent", description: `Check ${email} for your six-digit code.`, tone: "success" });
                    } else {
                        const result = await authClient.post<{ status: string; pendingId?: string; availableMethods?: string[] }>("/login/email/verify", {
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
                        } else toast.add({ title: "Sign-in could not continue", description: "The server returned an unrecognized authentication step.", tone: "danger" });
                    }
                } else {
                    const pending = registration ?? JSON.parse(sessionStorage.getItem("tallyvane.registration") ?? "null") as { userId: string; challengeId: string; email: string } | null;
                    if (!pending) throw new Error("This verification is no longer available. Start registration again.");
                    await authClient.post("/register/email/verify", { ...pending, code });
                    toast.add({ title: "Email verified", description: "Your account email has been confirmed.", tone: "success" });
                    sessionStorage.removeItem("tallyvane.registration"); setRegistration(null);
                    setNotice("Your email is verified. You can now sign in.");
                }
            } else if (kind === "forgot") {
                if (!passwordResetChallengeId) {
                    const issued = await authClient.post<{ challengeId: string }>("/password/forgot", { email });
                    setPasswordResetChallengeId(issued.challengeId);
                    setCode("");
                    toast.add({ title: "Reset code sent", description: `Check ${email} for your six-digit code.`, tone: "success" });
                } else {
                    await authClient.post("/password/reset", {
                        challengeId: passwordResetChallengeId,
                        email,
                        code,
                        newPassword: String(form.get("newPassword") ?? ""),
                    });
                    setPasswordResetChallengeId("");
                    setNotice("Your password has been updated. Your authenticator and other second-factor requirements remain in place.");
                    toast.add({ title: "Password updated", description: "You can now sign in with your new password.", tone: "success" });
                }
            } else {
                setNotice("This flow is shown in the UI preview. No request was sent to the server.");
            }
        } catch (reason) {
            if (reason instanceof AuthError && Object.keys(reason.fieldErrors).length) {
                const nextErrors = { ...reason.fieldErrors };
                setFieldErrors(nextErrors);
                const firstInvalid = Object.keys(nextErrors)[0];
                if (firstInvalid) window.requestAnimationFrame(() => document.querySelector<HTMLElement>(`[name="${CSS.escape(firstInvalid)}"]`)?.focus());
            } else {
                const detail = message(reason);
                toast.add({ title: "Authentication failed", description: detail, tone: "danger" });
            }
        }
        finally { setBusy(false); }
    }

    const [title, description] = headings[kind];
    const previewOnly = kind === "preview";
    return <AuthLayout><h1 className={styles.heading}>{previewOnly ? headings[preview][0] : title}</h1>
        <p className={styles.description}>{previewOnly ? headings[preview][1] : description}</p>
        {previewOnly && <nav className={styles.catalog} aria-label="Authentication pages">
            <label htmlFor="auth-preview-page">Preview screen</label>
            <select id="auth-preview-page" value={preview} onChange={event => { setPreview(event.target.value as AuthPageKind); setFieldErrors({}); setNotice(""); }}>
                {(["login", "register", "mfa", "enrollment", "forgot", "otp", "google", "callback"] as AuthPageKind[]).map(page => <option key={page} value={page}>{page}</option>)}
            </select>
        </nav>}
        {notice && <p role="status" className={styles.notice}>{notice}</p>}
        {renderPage(previewOnly ? preview : kind, { busy, submit, payload, code, setCode, factor, setFactor, availableMethods, showPassword, setShowPassword, email, setEmail, googleEnabled, registrationPending: registration !== null, emailCodeRequested: Boolean(emailSignInChallengeId), otpPurpose, passwordResetRequested: Boolean(passwordResetChallengeId), fieldErrors })}
        <p className={styles.bottom}>
            {kind === "login" && <>New to Tallyvane? <Link className={styles.link} href="/register">Create an account</Link></>}
            {kind === "register" && <>Already have an account? <Link className={styles.link} href="/login">Sign in</Link></>}
            {kind === "login" && <span className="block"><Link className={styles.link} href="/forgot-password">Forgot password?</Link></span>}
            {kind === "login" && <span className="block"><Link className={styles.link} href="/otp?purpose=login">Sign in with an email code</Link></span>}
            {!previewOnly && <span className="block"><Link className={styles.link} href="/auth-preview">Open authentication preview</Link></span>}
        </p>
    </AuthLayout>;
}

type PageProps = { busy: boolean; submit: (event: FormEvent<HTMLFormElement>) => void; payload: string; code: string; setCode: (value: string) => void; factor: string; setFactor: (value: string) => void; availableMethods: string[]; showPassword: boolean; setShowPassword: (value: boolean) => void; email: string; setEmail: (value: string) => void; googleEnabled: boolean; registrationPending: boolean; emailCodeRequested: boolean; otpPurpose: string; passwordResetRequested: boolean; fieldErrors: Record<string, string> };
function renderPage(kind: AuthPageKind, props: PageProps) {
    const { busy, submit, payload, code, setCode, factor, setFactor, showPassword, setShowPassword } = props;
    if (kind === "login" || kind === "register") return <>
        {props.googleEnabled ? <a className={styles.provider} href="/api/v1/auth/google/oauth/start"><span className={styles.methodIcon} aria-hidden="true">G</span>Continue with Google</a> : <button className={styles.provider} type="button" disabled><span className={styles.methodIcon} aria-hidden="true">G</span>Continue with Google <span className={styles.hint}>Not configured</span></button>}
        {kind === "register" ? <details className={styles.emailDisclosure}>
            <summary className={styles.disclosureSummary}><span className={styles.disclosureTitle}>Continue with email</span><span className={styles.hint}>Create your account with an email address and password</span></summary>
            <form className={styles.form} onSubmit={submit}>
                {field("name", "Name (optional)", <Input autoComplete="name" />, props.fieldErrors)}
                {field("email", "Email address", <Input type="email" autoComplete="email" required />, props.fieldErrors)}
                {field("password", "Password", <Input type={showPassword ? "text" : "password"} autoComplete="new-password" minLength={15} maxLength={128} required />, props.fieldErrors, "Use 15–128 characters. A memorable passphrase works well.")}
                <Button tone="ghost" size="sm" className={styles.quiet ?? ""} type="button" aria-pressed={showPassword} onClick={() => setShowPassword(!showPassword)}>{showPassword ? "Hide password" : "Show password"}</Button>
                <Button tone="primary" className={styles.full ?? ""} type="submit" loading={busy}>Create account</Button>
            </form>
        </details> : <>
            <div className={styles.divider}>or use your email</div>
            <form className={styles.form} onSubmit={submit}>
                {field("email", "Email address", <Input type="email" autoComplete="email" required />, props.fieldErrors)}
                {field("password", "Password", <Input type={showPassword ? "text" : "password"} autoComplete="current-password" minLength={15} maxLength={128} required />, props.fieldErrors)}
                <Button tone="ghost" size="sm" className={styles.quiet ?? ""} type="button" aria-pressed={showPassword} onClick={() => setShowPassword(!showPassword)}>{showPassword ? "Hide password" : "Show password"}</Button>
                <Button tone="primary" className={styles.full ?? ""} type="submit" loading={busy}>Sign in</Button>
            </form>
        </>}
        <p className={styles.hint}>Email-code sign-in and password recovery are shown in the preview. They require the email challenge service to be enabled.</p>
    </>;
    if (kind === "mfa") return <form className={styles.form} onSubmit={submit}>
        <div className={styles.field}><label className={styles.fieldLabel} htmlFor="auth-factor">Verification method</label><select id="auth-factor" name="factor" aria-invalid={Boolean(props.fieldErrors.factor)} aria-describedby={props.fieldErrors.factor ? "auth-factor-error" : undefined} value={factor} onChange={event => setFactor(event.target.value)}>{(props.availableMethods.length ? props.availableMethods : ["TOTP"]).map(method => <option key={method} value={method}>{method === "TOTP" ? "Authenticator app" : method === "BACKUP_CODE" ? "Backup code" : "Email code"}</option>)}</select>{props.fieldErrors.factor && <span id="auth-factor-error" className={styles.fieldError}>{props.fieldErrors.factor}</span>}</div>
        {field("code", factor === "TOTP" ? "Authenticator code" : "Backup code", <Input className={styles.codeInput ?? ""} value={code} onChange={event => setCode(event.target.value)} autoComplete="one-time-code" inputMode="numeric" required />, props.fieldErrors)}
        <Button tone="primary" className={styles.full ?? ""} type="submit" loading={busy}>Verify and continue</Button>
    </form>;
    if (kind === "enrollment") return <form className={styles.form} onSubmit={submit}>
        {payload && <><p className={styles.hint}>Add this URI manually in your authenticator app:</p><code className={styles.secret}>{payload}</code></>}
        {field("code", "Code from your authenticator", <Input className={styles.codeInput ?? ""} value={code} onChange={event => setCode(event.target.value)} inputMode="numeric" autoComplete="one-time-code" />, props.fieldErrors)}
        <Button tone="primary" className={styles.full ?? ""} type="submit" loading={busy}>{payload ? "Confirm authenticator" : "Set up authenticator"}</Button>
    </form>;
    if (kind === "preview") return <section className={styles.notice}><strong>Preview only.</strong> Use the selector above to inspect each planned screen. Actions in this catalog never send credentials or modify an account.</section>;
    if (kind === "google") return props.googleEnabled
        ? <a className={`${styles.provider} ${styles.full}`} href="/api/v1/auth/google/oauth/start">Continue with Google</a>
        : <p className={styles.hint}>Google sign-in is not configured on this server yet.</p>;
    if (kind === "otp" && props.registrationPending) return <form className={styles.form} onSubmit={submit}>
        <p className={styles.hint}>Enter the six-digit code sent to {props.email}.</p>
        {field("code", "Verification code", <Input className={styles.codeInput ?? ""} value={code} onChange={event => setCode(event.target.value)} inputMode="numeric" autoComplete="one-time-code" maxLength={6} required />, props.fieldErrors)}
        <Button tone="primary" className={styles.full ?? ""} type="submit" loading={busy}>Verify email</Button>
    </form>;
    if (kind === "otp" && !props.otpPurpose) return <p className={styles.hint} role="status">Preparing your verification step…</p>;
    if (kind === "otp" && props.otpPurpose === "login") return <form className={styles.form} onSubmit={submit}>
        {field("email", "Email address", <Input type="email" value={props.email} onChange={event => props.setEmail(event.target.value)} autoComplete="email" required disabled={props.emailCodeRequested} />, props.fieldErrors)}
        {props.emailCodeRequested && <>
            <p className={styles.hint}>Enter the six-digit code sent to {props.email}.</p>
            {field("code", "Verification code", <Input className={styles.codeInput ?? ""} value={code} onChange={event => setCode(event.target.value)} inputMode="numeric" autoComplete="one-time-code" maxLength={6} required />, props.fieldErrors)}
        </>}
        <Button tone="primary" className={styles.full ?? ""} type="submit" loading={busy}>{props.emailCodeRequested ? "Verify and continue" : "Email me a sign-in code"}</Button>
        {props.emailCodeRequested && <Button tone="ghost" size="sm" type="button" onClick={() => window.location.reload()}>Use a different email</Button>}
    </form>;
    return <form className={styles.form} onSubmit={submit}>
        {(kind === "forgot" || kind === "otp") && field("email", "Email address", <Input type="email" value={props.email} onChange={event => props.setEmail(event.target.value)} autoComplete="email" required disabled={kind === "forgot" && props.passwordResetRequested} />, props.fieldErrors)}
        {kind === "forgot" && props.passwordResetRequested && <>
            <p className={styles.hint}>If an account uses this address, a reset code has been sent. It expires in 10 minutes.</p>
            {field("code", "Verification code", <Input className={styles.codeInput ?? ""} value={code} onChange={event => setCode(event.target.value)} inputMode="numeric" autoComplete="one-time-code" maxLength={6} required />, props.fieldErrors)}
            {field("newPassword", "New password", <Input type="password" autoComplete="new-password" minLength={15} maxLength={128} required />, props.fieldErrors, "Use 15–128 characters. Your MFA settings will remain unchanged.")}
        </>}
        {kind === "callback" && <p role="status">Checking the OAuth response…</p>}
        <Button tone="primary" className={styles.full ?? ""} type="submit" loading={busy}>{kind === "forgot" ? props.passwordResetRequested ? "Reset password" : "Send reset code" : kind === "otp" ? "Email me a sign-in code" : kind === "callback" ? "Return to sign in" : "Continue"}</Button>
        {(kind !== "forgot" && kind !== "otp") && <p className={styles.hint}>This page is an interactive UI preview. The matching server flow is not configured yet.</p>}
    </form>;
}

function field(name: string, label: string, control: ReactElement<InputProps>, errors: Record<string, string>, help?: string) {
    const error = errors[name];
    const id = `auth-${name}`;
    const child = cloneElement(control, { name, id });
    return <Field label={label} {...(help ? { description: help } : {})} {...(error ? { error } : {})} required={Boolean(control.props.required)}>{child}</Field>;
}
