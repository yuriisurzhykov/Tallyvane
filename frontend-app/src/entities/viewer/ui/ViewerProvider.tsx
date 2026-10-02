"use client";

import { createContext, useContext, useMemo, type ReactNode } from "react";
import { Viewer } from "../model/Viewer";

const ViewerContext = createContext<Viewer | undefined>(undefined);

export interface ViewerProviderProps {
    readonly id: string;
    readonly name: string;
    readonly children: ReactNode;
}

/** Takes plain values, not a `Viewer`: a class instance cannot cross from a server component to a client one. */
export function ViewerProvider({ id, name, children }: ViewerProviderProps) {
    const viewer = useMemo(() => new Viewer(id, name), [id, name]);
    return <ViewerContext.Provider value={viewer}>{children}</ViewerContext.Provider>;
}

export function useViewer(): Viewer {
    const viewer = useContext(ViewerContext);
    if (viewer === undefined) {
        throw new Error("useViewer was called outside a ViewerProvider: this page is not behind the console gate.");
    }
    return viewer;
}
