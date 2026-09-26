import { AuthPage } from "@/views/authentication";
import { isSafeRelativePath } from "frontend-shared/lib/safe-relative-path";

export default async function LoginPage({ searchParams }: {
    readonly searchParams: Promise<{ readonly returnTo?: string | string[] }>;
}) {
    const params = await searchParams;
    const candidate = typeof params.returnTo === "string" && isSafeRelativePath(params.returnTo)
        ? params.returnTo
        : undefined;
    return <AuthPage kind="login" returnTo={candidate} />;
}
