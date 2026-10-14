import type { ReactNode } from "react";
import { headers } from "next/headers";
import { redirect } from "next/navigation";
import { ViewerProvider, Viewers } from "auth-kit/entities/viewer";
import { ReauthProvider } from "auth-kit/features/reauthenticate";
import { RETURN_PATH_HEADER } from "auth-kit/return-path-proxy";
import type { AuthTexts } from "auth-kit/texts";
import { serverApi } from "@/shared/api";

export interface ConsoleGateProps {
    readonly children: ReactNode;
    /** The words of the prompt that signs the person in again when the session ends under a page. */
    readonly texts: AuthTexts;
}

/**
 * Stands in front of every console page. Asks the server, with the visitor's own cookie, who they are:
 * nobody means the sign-in page, remembering where they were going; somebody means the page, drawn
 * for them from the first byte. The client then carries on with an API that can sign them in again.
 */
export async function ConsoleGate({ children, texts }: ConsoleGateProps) {
    const viewer = await new Viewers(await serverApi()).current();
    if (viewer === undefined) {
        const wanted = (await headers()).get(RETURN_PATH_HEADER);
        redirect(wanted === null ? "/login" : `/login?return=${encodeURIComponent(wanted)}`);
    }
    return (
        <ViewerProvider {...viewer.toProps()}>
            <ReauthProvider texts={texts}>{children}</ReauthProvider>
        </ViewerProvider>
    );
}
