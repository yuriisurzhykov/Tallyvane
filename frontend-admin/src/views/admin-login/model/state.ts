import type { AdminFactor } from "@/features/admin-login";

export type AdminLoginScreen = "checking" | "password" | "mfa" | "enrollment" | "denied" | "unavailable";

export interface AdminLoginState {
    readonly screen: AdminLoginScreen;
    readonly busy: boolean;
    readonly error: string;
    readonly notice: string;
    readonly email: string;
    readonly password: string;
    readonly pendingId: string;
    readonly availableMethods: AdminFactor[];
    readonly factor: AdminFactor;
    readonly code: string;
    readonly emailChallengeId: string;
    readonly otpauthUri: string;
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
    pendingId: "",
    availableMethods: [],
    factor: "TOTP",
    code: "",
    emailChallengeId: "",
    otpauthUri: "",
};

export function adminLoginReducer(state: AdminLoginState, action: AdminLoginAction): AdminLoginState {
    return { ...state, ...action.patch };
}
