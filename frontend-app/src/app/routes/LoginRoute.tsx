import { LoginPage } from "@/views/login";

type Param = string | string[] | undefined;

const first = (value: Param): string | undefined => (Array.isArray(value) ? value[0] : value);

export async function LoginRoute({ searchParams }: { readonly searchParams: Promise<Record<string, Param>> }) {
    const query = await searchParams;
    return <LoginPage problem={first(query.problem)} returnTo={first(query.return)} />;
}
