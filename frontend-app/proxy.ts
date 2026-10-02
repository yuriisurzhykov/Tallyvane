export { proxy } from "@/app/return-path-proxy";

/** Pages only: assets and the API are never gated and never need the address. */
export const config = { matcher: ["/((?!api|_next|.*\\..*).*)"] };
