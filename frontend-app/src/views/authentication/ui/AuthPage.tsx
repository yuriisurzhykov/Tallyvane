"use client";

import { Link } from "frontend-shared/ui/link";
import { useRouter } from "next/navigation";
import { useCallback } from "react";
import { AuthError } from "@/features/authentication";
import { useToast } from "frontend-shared/ui/toast";
import { AuthLayout } from "./AuthLayout";
import styles from "../../../widgets/authentication-step/ui/auth-step.module.css";
import type { AuthStringKey } from "@/features/authentication/model/strings";
import { useAuthStrings } from "@/features/authentication/model/strings";
import type { AuthPageKind } from "@/features/authentication/model/AuthPageKind";
import { renderAuthenticationStep } from "@/widgets/authentication-step";
import { Stack } from "frontend-shared/ui/stack";
import { Select } from "frontend-shared/ui/select";
import { Text } from "frontend-shared/ui/text";
import type { AuthPageState } from "../model/useAuthPageState";
import { useAuthPageState } from "../model/useAuthPageState";
import { useAuthPageEffects } from "../model/useAuthPageEffects";
import { useAuthOperations } from "../model/useAuthOperations";
import { appRoutes } from "@/shared/config";
import { defineSettingsSections } from "settings-kit/entities/settings-section";
import { SettingsNavigationGuardProvider } from "settings-kit/features/settings-navigation";
import { SettingsWorkspace } from "settings-kit/widgets/settings-workspace";
import { AppShell } from "frontend-shared/ui/app-shell";
import { AccountMenu } from "@/widgets/account-menu";
import { appNavItems } from "@/app/navigation";
import { AccountProfileSection } from "./AccountProfileSection";
import { AccountNotificationsSection } from "./AccountNotificationsSection";

const headings = {
    login: ["loginTitle", "loginDescription"], register: ["registerTitle", "registerDescription"],
    mfa: ["mfaTitle", "mfaTitleHelp"], enrollment: ["enrollTitle", "enrollmentDescription"],
    forgot: ["forgotTitle", "forgotHelp"], otp: ["verifyTitle", "otpHelp"],
    google: ["google", "googleAvailableHelp"], callback: ["oauthErrorTitle", "oauthErrorDescription"],
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

export function AuthPage({ kind, returnTo, settingsSection = "security" }: {
    kind: AuthPageKind;
    returnTo?: string | undefined;
    settingsSection?: "profile" | "notifications" | "security";
}) {
    const router = useRouter();
    const t = useAuthStrings("auth");
    const { actions: toast } = useToast();
    const pageState: AuthPageState = useAuthPageState();
    const navigate = useCallback((path: string, replace?: boolean) => {
        if (replace) router.replace(path);
        else router.push(path);
    }, [router]);
    useAuthPageEffects({ kind, state: pageState, t, toast, getErrorMessage: message });
    const operations = useAuthOperations({
        kind,
        t,
        notify: (title, description, tone) => {
            toast.add({ title, ...(description ? { description } : {}), tone });
        },
        navigate,
        state: pageState,
        successPath: returnTo ?? appRoutes.authenticatedHome,
    });
    const settingsSections = defineSettingsSections([
        {
            id: "profile",
            href: "/account/profile",
            label: t("settingsProfile"),
            description: t("settingsProfileDescription")
        },
        {
            id: "notifications",
            href: "/account/notifications",
            label: t("settingsNotifications"),
            description: t("settingsNotificationsDescription")
        },
        { id: "security", href: "/account/security", label: t("security"), description: t("securityDescription") },
    ]);

    if (kind !== "security") {
        return <AuthLayout>
            <AuthPageHeading kind={ kind } preview={ pageState.preview } t={ t }/>
            <AuthPageContent kind={ kind } state={ pageState } operations={ operations } t={ t }/>
            <AuthPageFooter kind={ kind } primaryMethods={ pageState.primaryMethods } t={ t }/>
        </AuthLayout>;
    }

    return <SettingsNavigationGuardProvider
        labels={ {
            title: t("unsavedSettingsTitle"),
            description: t("unsavedSettingsDescription"),
            stay: t("stayOnSettings"),
            leave: t("leaveSettings"),
        } }
        navigate={ path => {
            router.push(path);
        } }
    >
        <AppShell navItems={ appNavItems("settings") } title={ t("settingsNavigation") }
                  skipLinkLabel={ t("settingsSkipLink") } actions={ <AccountMenu/> }>
            <SettingsWorkspace
                sections={ settingsSections }
                activeSectionId={ settingsSection }
                labels={ {
                    navigation: t("settingsNavigation"),
                    openNavigation: t("openSettingsNavigation"),
                    closeNavigation: t("closeSettingsNavigation"),
                } }
            >
                { settingsSection === "profile" ? <AccountProfileSection t={ t }/> :
                    settingsSection === "notifications" ? <AccountNotificationsSection t={ t }/> :
                        <AuthPageContent kind={ kind } state={ pageState } operations={ operations } t={ t }/> }
            </SettingsWorkspace>
        </AppShell>
    </SettingsNavigationGuardProvider>;
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
    const previewCallback = previewOnly && displayedKind === "callback";
    return <>
        { previewOnly && <AuthPreviewSelector state={ state } t={ t }/> }
        { state.notice &&
            <Text as="p" variant="body" role="status" className={ styles.notice ?? "" }>{ state.notice }</Text> }
        { previewCallback ? <Text variant="body">{ t("previewActionNotice") }</Text> :
            renderAuthenticationStep(displayedKind, stepProps) }
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
        primaryMethods: state.primaryMethods, registrationPending: state.registration !== null,
        startGoogleSignIn: operations.startGoogleSignIn,
        registrationChallengeReady: Boolean(state.registration?.challengeId),
        registrationResendSeconds: state.registrationResendSeconds,
        resendRegistrationCode: operations.resendRegistrationCode,
        emailCodeRequested: Boolean(state.emailSignInChallengeId), otpPurpose: state.otpPurpose,
        fieldErrors: state.fieldErrors,
        clearFieldErrors: () => {
            state.setFieldErrors({});
        },
        sessions: state.sessions,
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

function AuthPageFooter({
                            kind,
                            primaryMethods,
                            t,
                        }: {
    kind: AuthPageKind;
    primaryMethods: AuthPageState["primaryMethods"];
    t: Translate;
}) {
    const links = kind === "login" ? [
        ["noAccountPrompt", "/register", "createAccountLink"],
        ["", "/forgot-password", "forgotPasswordLink"],
        ...(primaryMethods.includes("EMAIL_SIGN_IN_CODE")
            ? [["", "/otp?purpose=login", "signInEmailCodeLink"]]
            : []),
    ] : kind === "register" ? [["hasAccountPrompt", "/login", "signInLink"]] : [];
    return <Stack as="footer" gap="inline" className={ styles.bottom ?? "" }>
        { links.map(([prompt, href, label]) => <Text key={ href } variant="body">
            { prompt && <>{ t(prompt as AuthStringKey) } </> }
            <Link className={ styles.link ?? "" } href={ href ?? "/login" }>{ t(label as AuthStringKey) }</Link>
        </Text>) }
    </Stack>;
}
