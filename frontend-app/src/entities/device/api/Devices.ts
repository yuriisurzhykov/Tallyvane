import { ProblemError, type Api } from "frontend-shared/api";
import { Device } from "../model/Device";

/** The devices a person is signed in on, and what can be done to them. */
export class Devices {
    private readonly api: Api;

    public constructor(api: Api) {
        this.api = api;
    }

    public async list(): Promise<Device[]> {
        const { devices } = await this.api.get("/devices");
        return devices.map((wire) => new Device(wire));
    }

    /**
     * Ends the session on the device. On this one it is the session the cookie holds *now*, which is not the
     * one listed if the person has signed in again since (ADR-084): `/session` ends that one, where the listed
     * id would end the old one and leave the new one signed in. A device that has already gone (`404`) is as
     * signed out as the person wanted it, so that is not a failure.
     */
    public async signOut(device: Device): Promise<void> {
        if (device.isCurrent()) {
            await this.api.delete("/session");
            return;
        }
        await this.unlessGone(() => this.api.delete("/device/{id}", { params: { id: device.key() } }));
    }

    /** Every call is a new intention, so the chain gives it a key of its own. */
    public async rename(device: Device, name: string): Promise<void> {
        await this.api.put("/device-names/{id}", { name }, { params: { id: device.key() } });
    }

    public async signOutOthers(): Promise<void> {
        await this.api.delete("/other-devices");
    }

    private async unlessGone(action: () => Promise<unknown>): Promise<void> {
        try {
            await action();
        } catch (failure) {
            if (!(failure instanceof ProblemError && failure.kind() === "not-found")) {
                throw failure;
            }
        }
    }
}
