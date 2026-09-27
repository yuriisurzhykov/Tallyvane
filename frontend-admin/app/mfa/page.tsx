import type { AdminFactor } from "@/features/admin-login";
import { AdminLoginView } from "@/views/admin-login";

interface AdminMfaPageProps {
    readonly searchParams: Promise<{
        readonly pending_id?: string | string[];
        readonly recommended_method?: string | string[];
        readonly methods?: string | string[];
    }>;
}

function factor(value: string | string[] | undefined): AdminFactor | undefined {
    return value === "TOTP" || value === "EMAIL_OTP" ? value : undefined;
}

export default async function AdminMfaPage({ searchParams }: AdminMfaPageProps) {
    const params = await searchParams;
    const pendingId = typeof params.pending_id === "string" ? params.pending_id : "";
    const availableMethods = typeof params.methods === "string"
        ? params.methods.split(",").map(value => factor(value)).filter((value): value is AdminFactor => Boolean(value))
        : [];
    const recommendedMethod = factor(params.recommended_method);
    const initialPending = pendingId && recommendedMethod && availableMethods.includes(recommendedMethod)
        ? { pendingId, recommendedMethod, availableMethods }
        : undefined;

    return <AdminLoginView returnTo={null} {...(initialPending ? { initialPending } : {})} />;
}
