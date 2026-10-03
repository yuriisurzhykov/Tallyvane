"use client";

import { useEffect } from "react";
import { useRouter } from "next/navigation";
import { Spinner } from "frontend-shared/ui/spinner";

export interface RedirectProps {
    readonly to: string;
    /** What a screen reader hears while the page changes. */
    readonly label: string;
}

/** Replaces this page with another as soon as it appears, so the back button does not return to a page that only decided where to go. */
export function Redirect({ to, label }: RedirectProps) {
    const router = useRouter();
    useEffect(() => {
        router.replace(to);
    }, [router, to]);
    return <Spinner size="lg" label={label} />;
}
