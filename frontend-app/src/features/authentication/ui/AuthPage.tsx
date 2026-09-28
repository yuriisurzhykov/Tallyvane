"use client";

import { type AuthPageKind } from "../model/AuthPageKind";
import { AuthProvider } from "../model/AuthContext";
import { AuthPageHeading } from "./AuthPageHeading";
import { AuthPageContent } from "./AuthPageContent";
import { AuthPageFooter } from "./AuthPageFooter";
import { AuthLayout } from "@/views/authentication/ui/AuthLayout";

export function AuthPage({ kind, returnTo }: { kind: AuthPageKind; returnTo?: string }) {
    return (
        <AuthProvider kind={kind} returnTo={returnTo}>
            <AuthLayout>
                <AuthPageHeading />
                <AuthPageContent />
                <AuthPageFooter />
            </AuthLayout>
        </AuthProvider>
    );
}