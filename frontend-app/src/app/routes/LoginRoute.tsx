import { LoginPage } from "auth-kit/views/login";
import { AuthFrame } from "@/widgets/auth-frame";
import { authTexts } from "../config";

type Param = string | string[] | undefined;

const first = (value: Param): string | undefined => (Array.isArray(value) ? value[0] : value);

export async function LoginRoute({ searchParams }: { readonly searchParams: Promise<Record<string, Param>> }) {
    const query = await searchParams;
    return <LoginPage texts={authTexts} frame={AuthFrame} problem={first(query.problem)} returnTo={first(query.return)} />;
}
