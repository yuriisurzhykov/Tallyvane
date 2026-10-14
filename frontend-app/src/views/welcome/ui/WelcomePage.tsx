import { Link } from "frontend-shared/ui/link";
import { useStrings } from "@/shared/i18n";
import { AuthFrame } from "@/widgets/auth-frame";
import { WelcomeForm } from "./WelcomeForm";

export interface WelcomePageProps {
    readonly name: string;
    readonly email: string;
    /** Where the new account goes once it exists, when the person was not headed anywhere in particular. */
    readonly landing: string;
}

export function WelcomePage({ name, email, landing }: WelcomePageProps) {
    const t = useStrings("welcome");
    return (
        <AuthFrame title={t("title")} lead={t("signedInAs", { email })}>
            <WelcomeForm initialName={name} landing={landing} />
            <Link href="/login">{t("otherAccount")}</Link>
        </AuthFrame>
    );
}
