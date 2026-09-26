import { authClient } from "./client";

export type AccountAction = "CHANGE_PRIMARY_CREDENTIAL" | "MANAGE_SECOND_FACTORS";
export type ProofTokenKind = "PASSWORD" | "GOOGLE" | "EMAIL_SIGN_IN_CODE" | "TOTP" | "EMAIL_FACTOR_CODE" | "BACKUP_CODE";

export interface ActionSchemeOption {
    id: string;
    requiredTokens: ProofTokenKind[];
    assuranceRank: number;
}

export interface PresentedProofToken {
    kind: ProofTokenKind;
    value: string;
    challengeId?: string;
    codeVerifier?: string;
    redirectUri?: string;
}

export async function readActionSchemes(action: AccountAction): Promise<ActionSchemeOption[]> {
    const result = await authClient.get<{ schemes: ActionSchemeOption[] }>(
        `/account/action-proof/options?action=${encodeURIComponent(action)}`,
    );
    return result.schemes;
}

export async function requestActionEmailCode(action: AccountAction, kind: "EMAIL_SIGN_IN_CODE" | "EMAIL_FACTOR_CODE") {
    return authClient.post<{ challengeId: string }>("/account/action-proof/email-code", { action, kind });
}

export async function issueActionProof(action: AccountAction, tokens: PresentedProofToken[]) {
    return authClient.post<{ proof: string; expiresAt: string }>("/account/action-proof", { action, tokens });
}
