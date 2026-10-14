"use client";

import { useMemo } from "react";
import { useApi } from "frontend-shared/api";
import { useLoaded, type LoadedView } from "@/shared/lib";
import { PolicyVersions } from "../api/PolicyVersions";
import type { PolicyHistory } from "./PolicyHistory";
import type { Purpose } from "./PolicyForm";

/** The versions kept for a purpose as the server has them now. Another purpose is another question: the old answer is not shown for it. */
export function usePolicyHistory(purpose: Purpose): LoadedView<PolicyHistory> {
    const api = useApi();
    const load = useMemo(() => () => new PolicyVersions(api).history(purpose), [api, purpose]);
    return useLoaded(load);
}
