import { LoginVerifyPage } from "auth-kit/views/login-verify";
import { AuthFrame } from "@/widgets/auth-frame";
import { authTexts } from "../config";

/** No `recoverySetUpAt`: the authenticator is set up in the console, not here, so the page offers no way to it. */
export function LoginVerifyRoute() {
    return <LoginVerifyPage texts={authTexts} frame={AuthFrame} />;
}
