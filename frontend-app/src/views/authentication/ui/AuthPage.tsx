"use client";

import Link from "next/link";
import { useRouter } from "next/navigation";
import { AuthError } from "../../../features/authentication/api/client";
import { useToast } from "frontend-shared/ui/toast";
import { AuthLayout } from "./AuthLayout";
import styles from "../../../widgets/authentication-step/ui/auth-step.module.css";
import type { AuthStringKey } from "../../../features/authentication/model/strings";
import { useAuthStrings } from "../../../features/authentication/model/strings";
import type { AuthPageKind } from "../../../features/authentication/model/AuthPageKind";
import { renderAuthenticationStep } from "../../../widgets/authentication-step";
import { Stack } from "frontend-shared/ui/stack";
import { Select } from "frontend-shared/ui/select";
import { Text } from "frontend-shared/ui/text";
import { useAuthPageState } from "../model/useAuthPageState";
import type { AuthPageState } from "../model/useAuthPageState";
import { useAuthPageEffects } from "../model/useAuthPageEffects";
import { useAuthOperations } from "../model/useAuthOperations";
const headings = {
    login: ["loginTitle", "loginDescription"], register: ["registerTitle", "registerDescription"],
    mfa: ["mfaTitle", "mfaTitleHelp"], enrollment: ["enrollTitle", "enrollmentDescription"],
    forgot: ["forgotTitle", "forgotHelp"], otp: ["verifyTitle", "otpHelp"],
    google: ["google", "googleAvailableHelp"], callback: ["oauthTitle", "callbackDescription"],
    security: ["security", "securityDescription"],
    preview: ["demo", "previewDescription"],
} as const satisfies Record<AuthPageKind, readonly [AuthStringKey, AuthStringKey]>;

function message(error: unknown, t: (key: AuthStringKey) => string): string {
    if (!(error instanceof AuthError)) return t("requestFailed");
    if (error.status === 0) return t("networkError");
    if (error.status === 401) return t("sessionExpiredError");
    if (error.status === 403) return t("securitySessionError");
    if (error.status === 429) return t("rateLimitedError");
    return t("requestFailed");
}

export function AuthPage({ kind }: { kind: AuthPageKind }) {
    const router = useRouter();
    const t = useAuthStrings("auth");
    const { actions: toast } = useToast();
    const pageState: AuthPageState = useAuthPageState();
    useAuthPageEffects({ kind, state: pageState, t, toast, getErrorMessage: message });
    const operations = useAuthOperations({
        kind,
        t,
        notify: (title, description, tone) => {
            toast.add({ title, ...(description ? { description } : {}), tone });
        },
        navigate: path => { router.push(path); },
        state: pageState,
    });
    return <AuthLayout>
        <AuthPageHeading kind={ kind } preview={ pageState.preview } t={ t } />
        <AuthPageContent kind={ kind } state={ pageState } operations={ operations } t={ t } />
        <AuthPageFooter kind={ kind } t={ t } />
    </AuthLayout>;
}

type Translate = ReturnType<typeof useAuthStrings>;
type PageOperations = ReturnType<typeof useAuthOperations>;

function AuthPageHeading({ kind, preview, t }: { kind: AuthPageKind; preview: AuthPageKind; t: Translate }) {
    const displayedKind = kind === "preview" ? preview : kind;
    const [titleKey, descriptionKey] = headings[displayedKind];
    return <>
        <Text as="h1" variant="title1" className={ styles.heading ?? "" }>{ t(titleKey) }</Text>
        <Text as="p" variant="body" className={ styles.description ?? "" }>{ t(descriptionKey) }</Text>
    </>;
}

function AuthPageContent({
    kind, state, operations, t,
}: { kind: AuthPageKind; state: AuthPageState; operations: PageOperations; t: Translate }) {
    const previewOnly = kind === "preview";
    const displayedKind = previewOnly ? state.preview : kind;
    const stepProps = createStepProps(state, operations, t);
    return <>
        { previewOnly && <AuthPreviewSelector state={ state } t={ t } /> }
        { state.notice && !(kind === "enrollment" && state.requiredEnrollmentComplete) &&
            <Text as="p" variant="body" role="status" className={ styles.notice ?? "" }>{ state.notice }</Text> }
        { kind === "enrollment" && state.requiredEnrollmentComplete
            ? <EnrollmentComplete notice={ state.notice } t={ t } />
            : renderAuthenticationStep(displayedKind, stepProps) }
    </>;
}

function createStepProps(
    state: AuthPageState,
    operations: PageOperations,
    t: Translate,
) {
    return {
        busy: state.busy, submit: operations.submit, payload: state.payload, qrCode: state.qrCode,
        code: state.code, setCode: state.setCode, factor: state.factor,
        setFactor: (value: string) => {
            state.setFactor(value);
            state.setEmailMfaChallengeId("");
        },
        availableMethods: state.availableMethods,
        emailMfaCodeRequested: Boolean(state.emailMfaChallengeId), showPassword: state.showPassword,
        setShowPassword: state.setShowPassword, email: state.email, setEmail: state.setEmail,
        googleEnabled: state.googleEnabled, registrationPending: state.registration !== null,
        registrationChallengeReady: Boolean(state.registration?.challengeId),
        registrationResendSeconds: state.registrationResendSeconds,
        resendRegistrationCode: operations.resendRegistrationCode,
        emailCodeRequested: Boolean(state.emailSignInChallengeId), otpPurpose: state.otpPurpose,
        passwordResetRequested: Boolean(state.passwordResetChallengeId), fieldErrors: state.fieldErrors,
        clearFieldErrors: () => { state.setFieldErrors({}); },
        recoveryCodes: state.recoveryCodes, emailMfaEnrollmentChallengeId: state.emailMfaEnrollmentChallengeId,
        enrollEmailMfa: operations.enrollEmailMfa, issueRecoveryCodes: operations.submitRecoveryCodes,
        clearRecoveryCodes: operations.clearRecoveryCodes, sessions: state.sessions,
        revokeSession: operations.revokeSession, t,
    };
}

function AuthPreviewSelector({ state, t }: { state: AuthPageState; t: Translate }) {
    return <Stack as="section" gap="inline" aria-label={ t("demo") } className={ styles.catalog ?? "" }>
        <Select.Root value={ state.preview } onValueChange={ value => {
            if (!value) return;
            state.setPreview(value);
            state.setFieldErrors({});
            state.setNotice("");
        } }>
            <Select.Label>{ t("previewScreen") }</Select.Label>
            <Select.Trigger><Select.Value/><Select.Icon/></Select.Trigger>
            <Select.Popup>{ (["login", "register", "mfa", "enrollment", "forgot", "otp", "google", "callback", "security"] as AuthPageKind[]).map(page =>
                <Select.Item key={ page } value={ page }>{ t(headings[page][0]) }</Select.Item>) }</Select.Popup>
        </Select.Root>
    </Stack>;
}

function EnrollmentComplete({ notice, t }: { notice: string; t: Translate }) {
    return <Stack role="status" gap="inline" className={ styles.notice ?? "" }>
        <Text variant="body">{ notice }</Text>
        <Link className={ styles.link ?? "" } href="/login">{ t("signInLink") }</Link>
    </Stack>;
}

function AuthPageFooter({ kind, t }: { kind: AuthPageKind; t: Translate }) {
    const links = kind === "login" ? [
        ["noAccountPrompt", "/register", "createAccountLink"],
        ["", "/forgot-password", "forgotPasswordLink"],
        ["", "/otp?purpose=login", "signInEmailCodeLink"],
    ] : kind === "register" ? [["hasAccountPrompt", "/login", "signInLink"]] : [];
    return <Stack as="footer" gap="inline" className={ styles.bottom ?? "" }>
        { links.map(([prompt, href, label]) => <Text key={ href } variant="body">
            { prompt && <>{ t(prompt as AuthStringKey) } </> }
            <Link className={ styles.link ?? "" } href={ href ?? "/login" }>{ t(label as AuthStringKey) }</Link>
        </Text>) }
        { kind !== "preview" && <Text variant="body"><Link className={ styles.link ?? "" } href="/auth-preview">{ t("openPreview") }</Link></Text> }
    </Stack>;
}
