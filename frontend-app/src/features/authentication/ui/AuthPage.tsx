"use client";

import { useEffect, useState } from "react";
import type { FormEvent } from "react";
import Link from "next/link";
import { useRouter } from "next/navigation";
import { AuthError, authClient } from "../api/client";
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
    const [busy, setBusy] = useState(false);
    const [error, setError] = useState("");
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

    useEffect(() => {
        if (kind === "google") {
            const result = new URLSearchParams(window.location.search).get("error");
            if (result === "cancelled") setError("Google sign-in was cancelled. You can try again or use another sign-in method.");
            else if (result) setError("Google sign-in could not be completed. Check your account or try again.");
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
            try {
                const saved = sessionStorage.getItem("tallyvane.registration");
                if (saved) {
                    const parsed = JSON.parse(saved) as { userId: string; challengeId: string; email: string };
                    setRegistration(parsed); setEmail(parsed.email);
                }
            } catch { sessionStorage.removeItem("tallyvane.registration"); }
        }
    }, [kind]);

    async function submit(event: FormEvent<HTMLFormElement>) {
        event.preventDefault(); setBusy(true); setError(""); setNotice("");
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
                } else setError("The server returned an authentication step this page does not recognize.");
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
                    setNotice("Authenticator confirmed. Your account is protected."); setPayload(""); setCode("");
                }
            } else if (kind === "otp") {
                const pending = registration ?? JSON.parse(sessionStorage.getItem("tallyvane.registration") ?? "null") as { userId: string; challengeId: string; email: string } | null;
                if (!pending) throw new Error("This verification is no longer available. Start registration again.");
                await authClient.post("/register/email/verify", { ...pending, code });
                sessionStorage.removeItem("tallyvane.registration"); setRegistration(null);
                setNotice("Your email is verified. You can now sign in.");
            } else {
                setNotice("This flow is shown in the UI preview. No request was sent to the server.");
            }
        } catch (reason) { setError(message(reason)); }
        finally { setBusy(false); }
    }

    const [title, description] = headings[kind];
    const previewOnly = kind === "preview";
    return <AuthLayout><h1 className={styles.heading}>{previewOnly ? headings[preview][0] : title}</h1>
        <p className={styles.description}>{previewOnly ? headings[preview][1] : description}</p>
        {previewOnly && <nav className={styles.catalog} aria-label="Authentication pages">
            <label htmlFor="auth-preview-page">Preview screen</label>
            <select id="auth-preview-page" value={preview} onChange={event => { setPreview(event.target.value as AuthPageKind); setError(""); setNotice(""); }}>
                {(["login", "register", "mfa", "enrollment", "forgot", "otp", "google", "callback"] as AuthPageKind[]).map(page => <option key={page} value={page}>{page}</option>)}
            </select>
        </nav>}
        {(error || notice) && <p role={error ? "alert" : "status"} className={`${styles.notice} ${error ? styles.error : ""}`}>{error || notice}</p>}
        {renderPage(previewOnly ? preview : kind, { busy, submit, payload, code, setCode, factor, setFactor, availableMethods, showPassword, setShowPassword, email, setEmail, googleEnabled, registrationPending: registration !== null })}
        <p className={styles.bottom}>
            {kind === "login" && <>New to Tallyvane? <Link className={styles.link} href="/register">Create an account</Link></>}
            {kind === "register" && <>Already have an account? <Link className={styles.link} href="/login">Sign in</Link></>}
            {kind === "login" && <span className="block"><Link className={styles.link} href="/forgot-password">Forgot password?</Link></span>}
            {!previewOnly && <span className="block"><Link className={styles.link} href="/auth-preview">Open authentication preview</Link></span>}
        </p>
    </AuthLayout>;
}

type PageProps = { busy: boolean; submit: (event: FormEvent<HTMLFormElement>) => void; payload: string; code: string; setCode: (value: string) => void; factor: string; setFactor: (value: string) => void; availableMethods: string[]; showPassword: boolean; setShowPassword: (value: boolean) => void; email: string; setEmail: (value: string) => void; googleEnabled: boolean; registrationPending: boolean };
function renderPage(kind: AuthPageKind, props: PageProps) {
    const { busy, submit, payload, code, setCode, factor, setFactor, showPassword, setShowPassword } = props;
    if (kind === "login" || kind === "register") return <>
        {props.googleEnabled ? <a className={styles.method} href="/api/v1/auth/google/oauth/start"><span className={styles.methodIcon} aria-hidden="true">G</span><span className={styles.methodText}><strong>Continue with Google</strong></span></a> : <button className={styles.method} type="button" disabled><span className={styles.methodIcon} aria-hidden="true">G</span><span className={styles.methodText}><strong>Continue with Google</strong><span className={styles.hint}>Not configured on this server yet</span></span></button>}
        <div className={styles.divider}>or use your email</div>
        <form className={styles.form} onSubmit={submit}>
            {kind === "register" && <label className={styles.field}><span className={styles.fieldLabel}>Name (optional)</span><input name="name" autoComplete="name" /></label>}
            <label className={styles.field}><span className={styles.fieldLabel}>Email address</span><input name="email" type="email" autoComplete="email" required /></label>
            <label className={styles.field}><span className={styles.fieldLabel}>Password</span><input name="password" type={showPassword ? "text" : "password"} autoComplete={kind === "login" ? "current-password" : "new-password"} minLength={15} maxLength={128} required /><span className={styles.hint}>Use 15–128 characters. A memorable passphrase works well.</span></label>
            <button className={styles.method} type="button" aria-pressed={showPassword} onClick={() => setShowPassword(!showPassword)}>{showPassword ? "Hide password" : "Show password"}</button>
            <button className={`${styles.method} ${styles.full}`} type="submit" disabled={busy}>{busy ? "Please wait…" : kind === "login" ? "Sign in" : "Create account"}</button>
        </form>
        <p className={styles.hint}>Email-code sign-in and password recovery are shown in the preview. They require the email challenge service to be enabled.</p>
    </>;
    if (kind === "mfa") return <form className={styles.form} onSubmit={submit}>
        <label className={styles.field}><span className={styles.fieldLabel}>Verification method</span><select value={factor} onChange={event => setFactor(event.target.value)}>{(props.availableMethods.length ? props.availableMethods : ["TOTP"]).map(method => <option key={method} value={method}>{method === "TOTP" ? "Authenticator app" : method === "BACKUP_CODE" ? "Backup code" : "Email code"}</option>)}</select></label>
        <label className={styles.field}><span className={styles.fieldLabel}>{factor === "TOTP" ? "Authenticator code" : "Backup code"}</span><input className={styles.codeInput} value={code} onChange={event => setCode(event.target.value)} autoComplete="one-time-code" inputMode="numeric" required /></label>
        <button className={`${styles.method} ${styles.full}`} type="submit" disabled={busy}>{busy ? "Verifying…" : "Verify and continue"}</button>
    </form>;
    if (kind === "enrollment") return <form className={styles.form} onSubmit={submit}>
        {payload && <><p className={styles.hint}>Add this URI manually in your authenticator app:</p><code className={styles.secret}>{payload}</code></>}
        <label className={styles.field}><span className={styles.fieldLabel}>Code from your authenticator</span><input className={styles.codeInput} value={code} onChange={event => setCode(event.target.value)} inputMode="numeric" autoComplete="one-time-code" /></label>
        <button className={`${styles.method} ${styles.full}`} type="submit" disabled={busy}>{busy ? "Please wait…" : payload ? "Confirm authenticator" : "Set up authenticator"}</button>
    </form>;
    if (kind === "preview") return <section className={styles.notice}><strong>Preview only.</strong> Use the selector above to inspect each planned screen. Actions in this catalog never send credentials or modify an account.</section>;
    if (kind === "google") return props.googleEnabled
        ? <a className={`${styles.method} ${styles.full}`} href="/api/v1/auth/google/oauth/start">Continue with Google</a>
        : <p className={styles.hint}>Google sign-in is not configured on this server yet.</p>;
    if (kind === "otp" && props.registrationPending) return <form className={styles.form} onSubmit={submit}>
        <p className={styles.hint}>Enter the six-digit code sent to {props.email}.</p>
        <label className={styles.field}><span className={styles.fieldLabel}>Verification code</span><input className={styles.codeInput} value={code} onChange={event => setCode(event.target.value)} inputMode="numeric" autoComplete="one-time-code" maxLength={6} required /></label>
        <button className={`${styles.method} ${styles.full}`} type="submit" disabled={busy}>{busy ? "Verifying…" : "Verify email"}</button>
    </form>;
    return <form className={styles.form} onSubmit={submit}>
        {(kind === "forgot" || kind === "otp") && <label className={styles.field}><span className={styles.fieldLabel}>Email address</span><input type="email" value={props.email} onChange={event => props.setEmail(event.target.value)} autoComplete="email" required /></label>}
        {kind === "callback" && <p role="status">Checking the OAuth response…</p>}
        <button className={`${styles.method} ${styles.full}`} type="submit" disabled={busy}>{busy ? "Please wait…" : kind === "forgot" ? "Send reset code" : kind === "otp" ? "Email me a sign-in code" : kind === "callback" ? "Return to sign in" : "Continue"}</button>
        <p className={styles.hint}>This page is an interactive UI preview. The matching server flow is not configured yet.</p>
    </form>;
}
