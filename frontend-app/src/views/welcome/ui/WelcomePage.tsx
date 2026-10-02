import { Link } from "frontend-shared/ui/link";
import { useStrings } from "@/shared/i18n";
import { AuthFrame } from "@/widgets/auth-frame";
import { WelcomeForm } from "./WelcomeForm";

export interface WelcomePageProps {
    readonly name: string;
    readonly email: string;
}

export function WelcomePage({ name, email }: WelcomePageProps) {
    const t = useStrings("welcome");
    return (
        <AuthFrame title={t("title")} lead={t("signedInAs", { email })}>
            <WelcomeForm initialName={name} />
            <Link href="/login">{t("otherAccount")}</Link>
        </AuthFrame>
    );
}
