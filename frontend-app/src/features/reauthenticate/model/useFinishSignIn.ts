"use client";

import { useCallback } from "react";
import { useRouter } from "next/navigation";
import { ReturnPath } from "@/entities/viewer";
import { PopupHandoff } from "./PopupHandoff";

/**
 * What to do once a session exists. When this window was opened only to sign in again over a page that
 * is still waiting, tell that page and close; the answer is `true`, and the caller says so on screen in
 * case the browser will not close a window it did not open. Otherwise go where the person was headed.
 */
export function useFinishSignIn(): () => boolean {
    const router = useRouter();
    return useCallback(() => {
        if (new PopupHandoff().completeIfExpected()) {
            window.close();
            return true;
        }
        router.replace(new ReturnPath().take() ?? "/today");
        return false;
    }, [router]);
}
