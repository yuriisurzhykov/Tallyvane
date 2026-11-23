"use client";

import { useCallback, useEffect, useMemo, useState } from "react";
import { ProblemError, useApi } from "frontend-shared/api";
import { Career, type Home } from "@/entities/career";
import { Stories, type Story } from "@/entities/story";
import { useStrings } from "@/shared/i18n";

export interface HomeBoardState {
    readonly home: Home | null;
    readonly told: readonly Story[];
    /** The story the last words went into, shown until the person dismisses it. */
    readonly kept: Story | null;
    readonly words: string;
    readonly busy: boolean;
    readonly notice: string | null;
    /** The story the next words continue, when the step is to finish one. */
    readonly continuing: string | undefined;
    readonly setWords: (words: string) => void;
    readonly send: () => Promise<void>;
    readonly choose: (role: string) => Promise<void>;
    readonly dismissKept: () => void;
}

/** Loads the home and the stories, and carries what the person types and sends. */
export function useHomeBoard(): HomeBoardState {
    const t = useStrings("home");
    const api = useApi();
    const career = useMemo(() => new Career(api), [api]);
    const stories = useMemo(() => new Stories(api), [api]);
    const [home, setHome] = useState<Home | null>(null);
    const [told, setTold] = useState<Story[]>([]);
    const [kept, setKept] = useState<Story | null>(null);
    const [words, setWords] = useState("");
    const [busy, setBusy] = useState(false);
    const [notice, setNotice] = useState<string | null>(null);
    const [reads, setReads] = useState(0);
    // Strings, not `t`: the translator is a new function on every render and would refetch forever.
    const loadFailed = t("loadFailed");
    const sendFailed = t("sendFailed");

    useEffect(() => {
        let current = true;
        Promise.all([career.home(), stories.list()]).then(
            ([shown, list]) => {
                if (!current) return;
                setHome(shown);
                setTold(list);
            },
            () => {
                if (current) setNotice(loadFailed);
            },
        );
        return () => {
            current = false;
        };
    }, [career, stories, reads, loadFailed]);

    const continuing = home?.step === "finish_story" ? (home.subject ?? undefined) : undefined;

    const send = useCallback(async () => {
        setBusy(true);
        setNotice(null);
        try {
            setKept(await stories.tell(words, continuing));
            setWords("");
            setReads((count) => count + 1);
        } catch (failure) {
            setNotice(failure instanceof ProblemError ? (failure.detail() ?? sendFailed) : sendFailed);
        } finally {
            setBusy(false);
        }
    }, [words, continuing, stories, sendFailed]);

    const choose = useCallback(
        async (role: string) => {
            await career.choose(role);
            setReads((count) => count + 1);
        },
        [career],
    );

    const dismissKept = useCallback(() => { setKept(null); }, []);

    return { home, told, kept, words, busy, notice, continuing, setWords, send, choose, dismissKept };
}
