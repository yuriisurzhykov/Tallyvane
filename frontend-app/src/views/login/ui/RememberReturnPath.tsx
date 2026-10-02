"use client";

import { useEffect } from "react";
import { ReturnPath } from "@/entities/viewer";

export interface RememberReturnPathProps {
    readonly path: string | undefined;
    /** Set when Google sent the person back with a problem: a retry of the same sign-in, not a new one. */
    readonly isRetry: boolean;
}

/**
 * Puts the address the person was going to where it survives the trip to Google. A visit that brings
 * no address and is not a retry is a fresh sign-in, so whatever an abandoned attempt left is dropped.
 * Renders nothing.
 */
export function RememberReturnPath({ path, isRetry }: RememberReturnPathProps) {
    useEffect(() => {
        const returnPath = new ReturnPath();
        if (path !== undefined) {
            returnPath.remember(path);
        } else if (!isRetry) {
            returnPath.forget();
        }
    }, [path, isRetry]);
    return null;
}
