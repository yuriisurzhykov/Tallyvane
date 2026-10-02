"use client";

import { createContext, useContext, type ReactNode } from "react";
import type { Api } from "./Api";

const ApiContext = createContext<Api | null>(null);

export interface ApiProviderProps {
    readonly api: Api;
    readonly children: ReactNode;
}

export function ApiProvider({ api, children }: ApiProviderProps) {
    return <ApiContext value={api}>{children}</ApiContext>;
}

export function useApi(): Api {
    const api = useContext(ApiContext);
    if (api === null) {
        throw new Error("useApi was called outside an ApiProvider");
    }
    return api;
}
