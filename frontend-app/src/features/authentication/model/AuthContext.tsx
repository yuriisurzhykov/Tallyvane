"use client";

import { createContext, type ReactNode, useContext, useMemo } from "react";
import { useRouter } from "next/navigation";
import { useToast } from "frontend-shared/ui/toast";
import { appRoutes } from "@/shared/config";
import type { AuthPageKind } from "./AuthPageKind";
import { useAuthStrings } from "./strings";
import { parseAuthError } from "../lib/auth-errors";
import { useAuthPageState } from "@/views/authentication/model/useAuthPageState";
import { useAuthOperations } from "@/views/authentication/model/useAuthOperations";
import { useAuthPageEffects } from "@/views/authentication/model/useAuthPageEffects";

interface AuthContextType {
    kind: AuthPageKind;
    state: ReturnType<typeof useAuthPageState>;
    operations: ReturnType<typeof useAuthOperations>;
    t: ReturnType<typeof useAuthStrings>;
}

const AuthContext = createContext<AuthContextType | null>(null);

export function AuthProvider({
                                 kind,
                                 returnTo,
                                 children,
                             }: {
    kind: AuthPageKind;
    returnTo: string | undefined;
    children: ReactNode;
}) {
    const router = useRouter();
    const t = useAuthStrings("auth");
    const { actions: toast } = useToast();
    const state = useAuthPageState();

    const navigate = (path: string, replace?: boolean) => {
        if (replace) router.replace(path);
        else router.push(path);
    };

    useAuthPageEffects({ kind, state, t, toast, getErrorMessage: parseAuthError });

    const operations = useAuthOperations({
        kind,
        t,
        notify: (title, description, tone) => {
            toast.add({ title, ...(description ? { description } : {}), tone });
        },
        navigate,
        state,
        successPath: returnTo ?? appRoutes.authenticatedHome,
    });

    const value = useMemo(() => ({ kind, state, operations, t }), [kind, state, operations, t]);

    return <AuthContext.Provider value={ value }>{ children }</AuthContext.Provider>;
}

export const useAuth = () => {
    const ctx = useContext(AuthContext);
    if (!ctx) throw new Error("useAuth must be used within AuthProvider");
    return ctx;
};