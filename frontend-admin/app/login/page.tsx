import { AdminLoginView } from "@/views/admin-login";

interface LoginPageProps {
    readonly searchParams: Promise<{ readonly returnTo?: string | string[] }>;
}

export default async function LoginPage({ searchParams }: LoginPageProps) {
    const params = await searchParams;
    const candidate = typeof params.returnTo === "string" ? params.returnTo : null;
    return <AdminLoginView returnTo={candidate} />;
}
