import { Fragment, useState } from "react";
import type { ReactNode } from "react";
import { Separator } from "frontend-shared/ui/separator";
import { Stack } from "frontend-shared/ui/stack";
import { Surface } from "frontend-shared/ui/surface";
import { Text } from "frontend-shared/ui/text";
import type { Activation, Harmless } from "@/features/activate-version";
import type { Diff } from "@/shared/diff";
import { useStrings } from "@/shared/i18n";
import type { Listed } from "./Listed";
import { VersionRow } from "./VersionRow";

export interface VersionListProps<Version extends Listed, Consequences extends Harmless> {
    /** The versions that are not in force, newest first. */
    readonly versions: readonly Version[];
    readonly inForce: Version | undefined;
    readonly showSummary: (version: Version) => ReactNode;
    readonly differences: (version: Version, inForce: Version) => Diff;
    readonly activationFor: (version: Version) => Activation<Consequences>;
    readonly describe: (consequences: Consequences) => readonly string[];
    readonly onNewFrom: (version: Version) => void;
    /** A version was put in force or has gone: the list needs reading again. */
    readonly onChanged: () => void;
}

/** "Other versions": every version that is not in force, one quiet row each. Only one panel is open at a time. */
export function VersionList<Version extends Listed, Consequences extends Harmless>({
    versions,
    inForce,
    showSummary,
    differences,
    activationFor,
    describe,
    onNewFrom,
    onChanged,
}: VersionListProps<Version, Consequences>) {
    const t = useStrings("history");
    const [details, setDetails] = useState<number | undefined>(undefined);
    const [activating, setActivating] = useState<number | undefined>(undefined);

    return (
        <Surface variant="elevated" className="p-group-gap">
            <Stack gap="group-gap">
                <Text variant="title3">{t("title")}</Text>
                {versions.length === 0 ? <Text variant="body" color="secondary">{t("noOthers")}</Text> : null}
                <Stack gap="stack">
                    {versions.map((version, at) => {
                        const number = version.number();
                        return (
                            <Fragment key={number}>
                                {at > 0 ? <Separator decorative /> : null}
                                <VersionRow
                                    version={version}
                                    summary={showSummary(version)}
                                    rollback={inForce !== undefined && number < inForce.number()}
                                    diff={inForce === undefined ? undefined : differences(version, inForce)}
                                    inForceNumber={inForce?.number()}
                                    activation={activationFor(version)}
                                    describe={describe}
                                    detailsOpen={details === number}
                                    onToggleDetails={() => { setDetails(details === number ? undefined : number); }}
                                    activationOpen={activating === number}
                                    onOpenActivation={() => { setActivating(number); }}
                                    onCloseActivation={() => { setActivating(undefined); }}
                                    onChanged={() => { setActivating(undefined); onChanged(); }}
                                    onNewFromThis={() => { onNewFrom(version); }}
                                />
                            </Fragment>
                        );
                    })}
                </Stack>
            </Stack>
        </Surface>
    );
}
