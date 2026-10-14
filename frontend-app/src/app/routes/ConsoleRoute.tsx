import type { ReactNode } from "react";
import { ConsoleGate } from "@/widgets/console-gate";
import { authTexts } from "../config";

/** Every console page stands behind the gate, which asks for the words of the prompt that signs the person in again. */
export function ConsoleRoute({ children }: { readonly children: ReactNode }) {
    return <ConsoleGate texts={authTexts}>{children}</ConsoleGate>;
}
