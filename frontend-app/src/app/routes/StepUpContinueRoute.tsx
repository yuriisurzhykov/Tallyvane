import { StepUpContinuePage } from "auth-kit/views/step-up-continue";
import { AuthFrame } from "@/widgets/auth-frame";
import { authTexts } from "../config";

export function StepUpContinueRoute() {
    return <StepUpContinuePage texts={authTexts} frame={AuthFrame} />;
}
