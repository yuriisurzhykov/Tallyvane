import { act, renderHook } from "@testing-library/react";
import { afterEach, beforeEach, describe, expect, it, vi } from "vitest";
import { DEFAULT_AUTOSAVE_DEBOUNCE_MS, useDebouncedAutosave } from "./use-debounced-autosave";

/**
 * A controllable promise: tests need to assert the "pending" status exists
 * *before* deciding whether the save resolves or rejects, which a plain
 * `Promise.resolve()`/`Promise.reject()` mock cannot do — both settle before
 * the assertion ever runs.
 */
function deferred<T>() {
    let resolve!: (value: T) => void;
    let reject!: (reason: unknown) => void;
    const promise = new Promise<T>((res, rej) => {
        resolve = res;
        reject = rej;
    });
    return { promise, resolve, reject };
}

beforeEach(() => {
    vi.useFakeTimers();
});

afterEach(() => {
    vi.useRealTimers();
});

describe("useDebouncedAutosave", () => {
    it("does not save on mount", () => {
        const onSave = vi.fn().mockResolvedValue(undefined);
        const { result } = renderHook(() => useDebouncedAutosave({ value: "initial", onSave }));

        expect(result.current.status).toBe("idle");
        expect(onSave).not.toHaveBeenCalled();
    });

    it("saves the value 400ms after it stops changing, by default", async () => {
        const onSave = vi.fn().mockResolvedValue(undefined);
        const { result, rerender } = renderHook(({ value }) => useDebouncedAutosave({ value, onSave }), {
            initialProps: { value: "a" },
        });

        rerender({ value: "ab" });
        expect(result.current.status).toBe("pending");

        await act(async () => {
            await vi.advanceTimersByTimeAsync(DEFAULT_AUTOSAVE_DEBOUNCE_MS - 1);
        });
        expect(onSave).not.toHaveBeenCalled();

        await act(async () => {
            await vi.advanceTimersByTimeAsync(1);
        });
        expect(onSave).toHaveBeenCalledExactlyOnceWith("ab");
    });

    /**
     * The classic debounce assertion: five rapid changes inside one
     * debounce window must produce one save of the final value, not five
     * saves — otherwise this is just a delay, not a debounce.
     */
    it("collapses several rapid changes into a single save of the final value", async () => {
        const onSave = vi.fn().mockResolvedValue(undefined);
        const { rerender } = renderHook(({ value }) => useDebouncedAutosave({ value, onSave }), {
            initialProps: { value: "a" },
        });

        for (const next of ["ab", "abc", "abcd", "abcde"]) {
            rerender({ value: next });
            await act(async () => {
                await vi.advanceTimersByTimeAsync(100);
            });
        }
        await act(async () => {
            await vi.advanceTimersByTimeAsync(DEFAULT_AUTOSAVE_DEBOUNCE_MS);
        });

        expect(onSave).toHaveBeenCalledExactlyOnceWith("abcde");
    });

    it("respects a custom debounceMs", async () => {
        const onSave = vi.fn().mockResolvedValue(undefined);
        const { rerender } = renderHook(({ value }) => useDebouncedAutosave({ value, onSave, debounceMs: 0 }), {
            initialProps: { value: "a" },
        });

        rerender({ value: "b" });
        await act(async () => {
            await vi.advanceTimersByTimeAsync(0);
        });

        expect(onSave).toHaveBeenCalledExactlyOnceWith("b");
    });

    it("goes idle again once the save resolves", async () => {
        const onSave = vi.fn().mockResolvedValue(undefined);
        const { result, rerender } = renderHook(({ value }) => useDebouncedAutosave({ value, onSave }), {
            initialProps: { value: "a" },
        });

        rerender({ value: "ab" });
        await act(async () => {
            await vi.advanceTimersByTimeAsync(DEFAULT_AUTOSAVE_DEBOUNCE_MS);
        });

        expect(result.current.status).toBe("idle");
        expect(result.current.lastSavedValue).toBe("ab");
    });

    it("moves to \"error\" with the rejection reason when the save fails, and keeps `value` untouched", async () => {
        const failure = new Error("network down");
        const onSave = vi.fn().mockRejectedValue(failure);
        const { result, rerender } = renderHook(({ value }) => useDebouncedAutosave({ value, onSave }), {
            initialProps: { value: "a" },
        });

        rerender({ value: "ab" });
        await act(async () => {
            await vi.advanceTimersByTimeAsync(DEFAULT_AUTOSAVE_DEBOUNCE_MS);
        });

        expect(result.current.status).toBe("error");
        expect(result.current.error).toBe(failure);
        // The rollback decision this file's own doc comment resolves: the
        // hook never substitutes its own value for the caller's — there is
        // nothing here to assert reverted, because nothing was.
        expect(result.current.lastSavedValue).toBe("a");
    });

    it("retry() re-attempts the same value and can succeed after a prior failure", async () => {
        const onSave = vi.fn().mockRejectedValueOnce(new Error("first attempt failed")).mockResolvedValueOnce(undefined);
        const { result, rerender } = renderHook(({ value }) => useDebouncedAutosave({ value, onSave }), {
            initialProps: { value: "a" },
        });

        rerender({ value: "ab" });
        await act(async () => {
            await vi.advanceTimersByTimeAsync(DEFAULT_AUTOSAVE_DEBOUNCE_MS);
        });
        expect(result.current.status).toBe("error");

        await act(() => {
            result.current.retry();
            return Promise.resolve();
        });

        expect(onSave).toHaveBeenCalledTimes(2);
        expect(result.current.status).toBe("idle");
        expect(result.current.error).toBeUndefined();
        expect(result.current.lastSavedValue).toBe("ab");
    });

    it("flush() saves immediately, without waiting out the remaining debounce", async () => {
        const { promise, resolve } = deferred<void>();
        const onSave = vi.fn().mockReturnValue(promise);
        const { result, rerender } = renderHook(({ value }) => useDebouncedAutosave({ value, onSave }), {
            initialProps: { value: "a" },
        });

        rerender({ value: "ab" });
        act(() => {
            result.current.flush();
        });

        // Not yet 400ms — a debounce-driven save would not have fired.
        expect(onSave).toHaveBeenCalledExactlyOnceWith("ab");

        await act(async () => {
            resolve();
            await promise;
        });
        expect(result.current.status).toBe("idle");
    });

    it("flush() is a no-op when there is nothing unsaved", () => {
        const onSave = vi.fn().mockResolvedValue(undefined);
        const { result } = renderHook(() => useDebouncedAutosave({ value: "a", onSave }));

        act(() => {
            result.current.flush();
        });

        expect(onSave).not.toHaveBeenCalled();
    });

    /**
     * A newer value arriving mid-flight must win: the save this replaces
     * should not be allowed to report `lastSavedValue`/`status` for a value
     * the caller has already moved past.
     */
    it("a superseded in-flight save does not overwrite the status of a newer one", async () => {
        const first = deferred<void>();
        const second = deferred<void>();
        const onSave = vi.fn().mockReturnValueOnce(first.promise).mockReturnValueOnce(second.promise);
        const { result, rerender } = renderHook(({ value }) => useDebouncedAutosave({ value, onSave }), {
            initialProps: { value: "a" },
        });

        rerender({ value: "ab" });
        await act(async () => {
            await vi.advanceTimersByTimeAsync(DEFAULT_AUTOSAVE_DEBOUNCE_MS);
        });
        rerender({ value: "abc" });
        await act(async () => {
            await vi.advanceTimersByTimeAsync(DEFAULT_AUTOSAVE_DEBOUNCE_MS);
        });

        expect(onSave).toHaveBeenCalledTimes(2);

        await act(async () => {
            first.resolve();
            await first.promise;
        });
        // The stale first save resolving must not mark "ab" as the last saved value.
        expect(result.current.lastSavedValue).toBe("a");
        expect(result.current.status).toBe("pending");

        await act(async () => {
            second.resolve();
            await second.promise;
        });
        expect(result.current.lastSavedValue).toBe("abc");
        expect(result.current.status).toBe("idle");
    });
});
