"use client";

import { useRouter } from "next/navigation";
import { useApi } from "frontend-shared/api";
import { ReturnPath } from "@/entities/viewer";
import { RegisterForm } from "@/features/register";
import { Sessions } from "@/features/open-session";

/** An account made, the person is signed in without going back to Google. */
export function WelcomeForm({ initialName }: { readonly initialName: string }) {
    const api = useApi();
    const router = useRouter();

    const signIn = async () => {
        await new Sessions(api).open();
        router.replace(new ReturnPath().take() ?? "/today");
    };

    return <RegisterForm initialName={initialName} onRegistered={signIn} />;
}
