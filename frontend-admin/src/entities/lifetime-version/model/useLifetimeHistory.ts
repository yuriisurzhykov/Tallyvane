"use client";

import { useMemo } from "react";
import { useApi } from "frontend-shared/api";
import { useLoaded, type LoadedView } from "@/shared/lib";
import { LifetimeVersions } from "../api/LifetimeVersions";
import type { Client } from "./LifetimeForm";
import type { LifetimeHistory } from "./LifetimeHistory";

/** The versions kept for a kind of client as the server has them now. Another client is another question: the old answer is not shown for it. */
export function useLifetimeHistory(client: Client): LoadedView<LifetimeHistory> {
    const api = useApi();
    const load = useMemo(() => () => new LifetimeVersions(api).history(client), [api, client]);
    return useLoaded(load);
}
