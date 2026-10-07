"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { useApi } from "frontend-shared/api";
import { SecurityActivity } from "../api/SecurityActivity";
import type { ActivityLog } from "./ActivityLog";

export type ActivityState =
    | { readonly status: "loading" }
    /** `more` is what the last "show more" came to: it failing leaves the log as it was. */
    | { readonly status: "ready"; readonly log: ActivityLog; readonly more: "idle" | "loading" | "failed" }
    | { readonly status: "failed" };

export interface ActivityView {
    readonly state: ActivityState;
    /** Reads the page after the last entry and adds it to the log. */
    readonly showMore: () => void;
    /** Reads the newest entries again, after the first read failed. */
    readonly reload: () => void;
}

/** The person's journal as the server has it now, read when the screen opens, and a page more whenever asked. */
export function useSecurityActivity(): ActivityView {
    const api = useApi();
    const activity = useMemo(() => new SecurityActivity(api), [api]);
    const [state, setState] = useState<ActivityState>({ status: "loading" });
    const [reads, setReads] = useState(0);

    useEffect(() => {
        let current = true;
        activity.page(undefined).then(
            (log) => {
                if (current) setState({ status: "ready", log, more: "idle" });
            },
            () => {
                if (current) setState({ status: "failed" });
            },
        );
        return () => {
            current = false;
        };
    }, [activity, reads]);

    const showMore = useCallback(() => {
        if (state.status !== "ready" || state.more === "loading") {
            return;
        }
        const { log } = state;
        setState({ status: "ready", log, more: "loading" });
        activity.page(log.after()).then(
            (page) => { setState({ status: "ready", log: log.andThen(page), more: "idle" }); },
            () => { setState({ status: "ready", log, more: "failed" }); },
        );
    }, [activity, state]);

    const reload = useCallback(() => {
        setState({ status: "loading" });
        setReads((count) => count + 1);
    }, []);

    return { state, showMore, reload };
}
