"use client";

import { useEffect } from "react";
import { ReturnPath } from "@/entities/viewer";

/** Puts the address the person was going to where it survives the trip to Google. Renders nothing. */
export function RememberReturnPath({ path }: { readonly path: string | undefined }) {
    useEffect(() => {
        new ReturnPath().remember(path);
    }, [path]);
    return null;
}
