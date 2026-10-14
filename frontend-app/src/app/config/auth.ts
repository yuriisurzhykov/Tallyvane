import type { AuthTexts } from "auth-kit/texts";
import { enDictionary } from "@/shared/i18n";

/** The words of the sign-in screens, taken from this app's dictionary. A namespace or a key missing from it is a compile error. */
export const authTexts: AuthTexts = {
    auth: enDictionary.auth,
    login: enDictionary.login,
    continue: enDictionary.continue,
    verify: enDictionary.verify,
    secondFactor: enDictionary.secondFactor,
    recoveryUsed: enDictionary.recoveryUsed,
    reauth: enDictionary.reauth,
    stepUp: enDictionary.stepUp,
    stepUpContinue: enDictionary.stepUpContinue,
};

/** Where a finished sign-in goes when the person was not headed anywhere in particular. */
export const SIGN_IN_LANDING = "/today";

/** Where a person who spent a recovery code is sent to set the authenticator up again. */
export const AUTHENTICATOR_SET_UP = "/settings/security";
