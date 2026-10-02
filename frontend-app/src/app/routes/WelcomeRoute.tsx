import { redirect } from "next/navigation";
import { ProblemError, type components } from "frontend-shared/api";
import { serverApi } from "@/shared/api";
import { WelcomePage } from "@/views/welcome";

type Confirmed = components["schemas"]["Welcomed"];

/** What Google confirmed about the person (the attempt cookie travels with the request), or nothing if there is no attempt. */
async function confirmed(): Promise<Confirmed | undefined> {
    try {
        return await (await serverApi()).get("/welcome");
    } catch (failure) {
        if (failure instanceof ProblemError) {
            return undefined;
        }
        throw failure;
    }
}

/** No attempt, or one that ended, means the person has nothing to confirm here: back to the start. */
export async function WelcomeRoute() {
    const found = await confirmed();
    if (found === undefined) {
        redirect("/login?problem=restart");
    }
    return <WelcomePage name={found.name} email={found.email} />;
}
