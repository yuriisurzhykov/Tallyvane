import { LoginContinuePage } from "auth-kit/views/login-continue";
import { AuthFrame } from "@/widgets/auth-frame";
import { authTexts, SIGN_IN_LANDING } from "../config";

export function LoginContinueRoute() {
    return <LoginContinuePage texts={authTexts} frame={AuthFrame} landing={SIGN_IN_LANDING} />;
}
