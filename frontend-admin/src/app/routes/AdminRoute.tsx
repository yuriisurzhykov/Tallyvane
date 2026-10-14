import type { ReactNode } from "react";
import { AdminGate } from "@/widgets/admin-gate";
import { authTexts } from "../config";

/** Every admin page stands behind the gate, which asks for the words of the prompt that signs the person in again. */
export function AdminRoute({ children }: { readonly children: ReactNode }) {
    return <AdminGate texts={authTexts}>{children}</AdminGate>;
}
