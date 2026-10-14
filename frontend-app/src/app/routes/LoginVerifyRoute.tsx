import { LoginVerifyPage } from "auth-kit/views/login-verify";
import { AuthFrame } from "@/widgets/auth-frame";
import { authTexts, AUTHENTICATOR_SET_UP } from "../config";

export function LoginVerifyRoute() {
    return <LoginVerifyPage texts={authTexts} frame={AuthFrame} recoverySetUpAt={AUTHENTICATOR_SET_UP} />;
}
