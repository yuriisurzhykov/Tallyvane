"use client";

import { useState, type ReactNode } from "react";
import { ApiProvider, createApi } from "frontend-shared/api";

/**
 * The plain API: same origin, no sign-in-again handling. What signed-out pages and the sign-in prompt
 * itself talk through. The console puts a guarded one below it (`ReauthProvider`).
 */
export function AppProviders({ children }: { readonly children: ReactNode }) {
    const [api] = useState(() => createApi({ origin: "" }));
    return <ApiProvider api={api}>{children}</ApiProvider>;
}
