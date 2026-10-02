import { cookies, headers } from "next/headers";
import { createApi, type Api } from "frontend-shared/api";

/**
 * The API as a server component sees it: the visitor's own `cookie` and `host` forwarded to the address
 * the backend answers on inside the network. Nothing else of the visitor's request is passed on.
 * Built per request, because what it carries belongs to one visitor.
 */
export async function serverApi(): Promise<Api> {
    // First, so that a page using this is rendered per request and never during the build.
    const [jar, incoming] = await Promise.all([cookies(), headers()]);
    const origin = process.env.TALLYVANE_API_INTERNAL_URL;
    if (origin === undefined || origin === "") {
        throw new Error("TALLYVANE_API_INTERNAL_URL is not set: the server cannot reach the API to check who is visiting.");
    }
    const host = incoming.get("host");
    return createApi({
        origin,
        headers: { cookie: jar.toString(), ...(host !== null ? { host } : {}) },
    });
}
