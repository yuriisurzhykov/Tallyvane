import { NextResponse, type NextRequest } from "next/server";

/** Tells the console gate which page was asked for, since a server component cannot see its own address. */
export function proxy(request: NextRequest): NextResponse {
    const headers = new Headers(request.headers);
    headers.set("x-return-path", `${request.nextUrl.pathname}${request.nextUrl.search}`);
    return NextResponse.next({ request: { headers } });
}
