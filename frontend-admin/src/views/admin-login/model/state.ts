import type { AdminFactor, AdminPrimarySignInMethod } from "@/features/admin-login";

export type AdminLoginScreen = "checking" | "password" | "mfa" | "denied" | "unavailable";

export interface AdminLoginState {
    readonly screen: AdminLoginScreen;
    readonly busy: boolean;
    readonly error: string;
    readonly notice: string;
    readonly email: string;
    readonly password: string;
    readonly signInMode: "PASSWORD" | "EMAIL_SIGN_IN_CODE";
    readonly emailSignInChallengeId: string;
    readonly pendingId: string;
    readonly availableMethods: AdminFactor[];
    readonly primaryMethods: AdminPrimarySignInMethod[];
    readonly factor: AdminFactor;
    readonly code: string;
    readonly emailChallengeId: string;
}

export interface AdminLoginAction {
    readonly type: "patch";
    readonly patch: Partial<AdminLoginState>;
}

export const initialAdminLoginState: AdminLoginState = {
    screen: "checking",
    busy: false,
    error: "",
    notice: "",
    email: "",
    password: "",
    signInMode: "PASSWORD",
    emailSignInChallengeId: "",
    pendingId: "",
    availableMethods: [],
    primaryMethods: [],
    factor: "TOTP",
    code: "",
    emailChallengeId: "",
};

export function adminLoginReducer(state: AdminLoginState, action: AdminLoginAction): AdminLoginState {
    return { ...state, ...action.patch };
}
