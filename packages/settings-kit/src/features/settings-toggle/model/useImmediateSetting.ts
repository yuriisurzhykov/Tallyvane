"use client";

import { useCallback, useEffect, useRef, useState } from "react";

export type ImmediateSettingStatus = "idle" | "saving" | "saved" | "error";

export interface UseImmediateSettingOptions {
    readonly value: boolean;
    readonly onSave: (value: boolean) => Promise<void>;
    readonly onError?: (error: unknown) => void;
}

export interface UseImmediateSettingResult {
    readonly value: boolean;
    readonly status: ImmediateSettingStatus;
    readonly setValue: (value: boolean) => void;
    readonly retry: () => void;
}

/** Owns the optimistic switch value, serializes rapid changes, and rolls back to the confirmed value on failure. */
export function useImmediateSetting({ value, onSave, onError }: UseImmediateSettingOptions): UseImmediateSettingResult {
    const [displayValue, setDisplayValue] = useState(value);
    const [status, setStatus] = useState<ImmediateSettingStatus>("idle");
    const confirmedValue = useRef(value);
    const requestedValue = useRef(value);
    const failedValue = useRef<boolean | null>(null);
    const saving = useRef(false);
    const onSaveRef = useRef(onSave);
    const onErrorRef = useRef(onError);
    const savedTimer = useRef<ReturnType<typeof setTimeout> | null>(null);

    onSaveRef.current = onSave;
    onErrorRef.current = onError;

    useEffect(() => {
        if (saving.current) return;
        confirmedValue.current = value;
        requestedValue.current = value;
        setDisplayValue(value);
    }, [value]);

    useEffect(() => () => {
        if (savedTimer.current) clearTimeout(savedTimer.current);
    }, []);

    const saveRequestedValue = useCallback(async () => {
        if (saving.current) return;
        saving.current = true;
        setStatus("saving");

        while (!Object.is(requestedValue.current, confirmedValue.current)) {
            const nextValue = requestedValue.current;
            try {
                await onSaveRef.current(nextValue);
                confirmedValue.current = nextValue;
            } catch (error) {
                const latestRequestedValue = requestedValue.current;
                const stillNeedsSaving = !Object.is(latestRequestedValue, confirmedValue.current);
                failedValue.current = stillNeedsSaving ? latestRequestedValue : null;
                requestedValue.current = confirmedValue.current;
                setDisplayValue(confirmedValue.current);
                setStatus(stillNeedsSaving ? "error" : "idle");
                saving.current = false;
                if (stillNeedsSaving) onErrorRef.current?.(error);
                return;
            }
        }

        saving.current = false;
        setStatus("saved");
        if (savedTimer.current) clearTimeout(savedTimer.current);
        savedTimer.current = setTimeout(() => { setStatus("idle"); }, 1800);
    }, []);

    const setValue = useCallback((nextValue: boolean) => {
        if (Object.is(nextValue, requestedValue.current)) return;
        failedValue.current = null;
        requestedValue.current = nextValue;
        setDisplayValue(nextValue);
        if (savedTimer.current) clearTimeout(savedTimer.current);
        void saveRequestedValue();
    }, [saveRequestedValue]);

    const retry = useCallback(() => {
        const retryValue = failedValue.current;
        if (retryValue === null) return;
        failedValue.current = null;
        if (Object.is(retryValue, confirmedValue.current)) {
            setDisplayValue(confirmedValue.current);
            setStatus("idle");
            return;
        }
        requestedValue.current = retryValue;
        setDisplayValue(retryValue);
        void saveRequestedValue();
    }, [saveRequestedValue]);

    return { value: displayValue, status, setValue, retry };
}
