import { NextResponse, type NextRequest } from "next/server";

/** The request header the sign-in gate of an app reads to learn which page was asked for. */
export const RETURN_PATH_HEADER = "x-return-path";

/** Tells the gate which page was asked for, since a server component cannot see its own address. */
export function proxy(request: NextRequest): NextResponse {
    const headers = new Headers(request.headers);
    headers.set(RETURN_PATH_HEADER, `${request.nextUrl.pathname}${request.nextUrl.search}`);
    return NextResponse.next({ request: { headers } });
}
