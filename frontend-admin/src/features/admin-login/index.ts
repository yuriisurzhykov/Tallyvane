export { AdminAuthError, adminAuthClient, adminSessionRuntime } from "./api";
export type {
    AdminAccountAction,
    AdminFactor,
    AdminPrimarySignInMethod,
    AdminPresentedProofToken,
    AdminProofScheme,
    AdminProofTokenKind,
    SignInOutcome,
} from "./api";
export { classifyAdminAccessStatus, resolveAdminLoginReturnTo } from "./model";
export type { AdminAccessStatus } from "./model";
export { useAdminLoginStrings } from "./model/strings";
