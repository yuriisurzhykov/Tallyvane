import type { ReactNode } from "react";
import { headers } from "next/headers";
import { redirect } from "next/navigation";
import { ViewerProvider, Viewers } from "@/entities/viewer";
import { ReauthProvider } from "@/features/reauthenticate";
import { serverApi } from "@/shared/api";

/**
 * Stands in front of every console page. Asks the server, with the visitor's own cookie, who they are:
 * nobody means the sign-in page, remembering where they were going; somebody means the page, drawn
 * for them from the first byte. The client then carries on with an API that can sign them in again.
 */
export async function ConsoleGate({ children }: { readonly children: ReactNode }) {
    const viewer = await new Viewers(await serverApi()).current();
    if (viewer === undefined) {
        const wanted = (await headers()).get("x-return-path");
        redirect(wanted === null ? "/login" : `/login?return=${encodeURIComponent(wanted)}`);
    }
    return (
        <ViewerProvider {...viewer.toProps()}>
            <ReauthProvider>{children}</ReauthProvider>
        </ViewerProvider>
    );
}
