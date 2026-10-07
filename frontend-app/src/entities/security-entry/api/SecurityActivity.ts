import type { Api } from "frontend-shared/api";
import { ActivityLog } from "../model/ActivityLog";
import { SecurityEntry } from "../model/SecurityEntry";

/** The person's security journal, a page at a time. */
export class SecurityActivity {
    private readonly api: Api;

    public constructor(api: Api) {
        this.api = api;
    }

    /** The newest entries, or those after the cursor a page before gave. */
    public async page(after: string | undefined): Promise<ActivityLog> {
        const { entries, next } = await this.api.get("/security-activity", {
            query: after === undefined ? {} : { before: after },
        });
        return new ActivityLog(entries.map((wire) => new SecurityEntry(wire)), next);
    }
}
