"use client";

import { createContext, use, useState, type ReactNode } from "react";
import { Toast as BaseToast } from "@base-ui/react/toast";
import type { ToastManager } from "@base-ui/react/toast";
import { Dot } from "../dot";
import { IconButton } from "../icon-button";

export type ToastTone = "neutral" | "info" | "success" | "danger" | "attention";

export interface ToastActionOptions {
    readonly label: ReactNode;
    readonly onAction: () => void;
}

/**
 * `tone`, not Base UI's own field name `type` — this project's tone
 * vocabulary is a hard rule (`COMPONENTS.md` §12: "Every component that
 * varies by meaning takes `tone`... No component takes a colour"), and
 * `type` reads as a second, competing axis to a caller already used to
 * `tone` everywhere else. Translated onto Base UI's real `type` field
 * internally (see `toBaseOptions` below) — `type`'s own doc comment
 * ("used to conditionally style the toast") is exactly this use, so this
 * is a rename for clarity, not a parallel piece of state.
 */
export interface AddToastOptions<Data extends object = Record<string, never>> {
    readonly title?: ReactNode;
    readonly description?: ReactNode;
    /** @default "neutral" */
    readonly tone?: ToastTone;
    /**
     * Milliseconds before auto-dismiss; `0` disables it.
     * @default 5000 — `ToastProvider`'s own default.
     */
    readonly timeout?: number;
    /** The host of undo: a single action button, e.g. `{ label: "Undo", onAction: restore }`. */
    readonly action?: ToastActionOptions;
    readonly data?: Data;
    /** Adding with an existing id updates that toast in place and refreshes its auto-dismiss timer (Base UI's own behaviour). */
    readonly id?: string;
}

export interface ToastSnapshot<Data extends object = Record<string, never>> {
    readonly id: string;
    readonly title?: ReactNode;
    readonly description?: ReactNode;
    readonly tone: ToastTone;
    readonly data?: Data;
}

export interface ToastRegionState<Data extends object = Record<string, never>> {
    /** The current queue, oldest first — Base UI's own order. */
    readonly toasts: readonly ToastSnapshot<Data>[];
}

export interface ToastRegionActions<Data extends object = Record<string, never>> {
    /** Returns the new toast's id. */
    readonly add: (options: AddToastOptions<Data>) => string;
    /** Closes one toast, or every toast when `id` is omitted. */
    readonly close: (id?: string) => void;
    readonly update: (id: string, options: Partial<AddToastOptions<Data>>) => void;
}

export interface ToastRegionMeta<Data extends object = Record<string, never>> {
    /**
     * The same stable handle passed to `<ToastRegion manager>` (or created
     * internally if omitted) — usable to fire a toast from outside the
     * render tree entirely, e.g. a `QueryClient`'s global `onError`, which
     * is the realistic origin of "the failure channel for optimistic
     * writes" (`COMPONENTS.md`): the mutation hook that catches the
     * failure is not always a descendant of wherever `<ToastRegion>` itself
     * is mounted, but it is always within the same `toastManager` boundary.
     */
    readonly manager: ToastManager<Data>;
}

function toBaseOptions<Data extends object>(options: AddToastOptions<Data>) {
    const { tone, action, ...rest } = options;
    return {
        ...rest,
        type: tone ?? "neutral",
        ...(action ? { actionProps: { children: action.label, onClick: action.onAction } } : {}),
    };
}

/**
 * `Record<string, unknown>`, not `any`: one context feeds every
 * `useToast<Data>()` call in the tree, each of which may name a different
 * `Data` — the manager itself is genuinely polymorphic over that type, so
 * some type erasure at this one seam is correct, not a shortcut. `useToast`
 * below re-asserts the caller's own `Data` on the way out, which is the
 * actual type-safety boundary; this container only needs to hold a value
 * shaped like *some* object.
 */
const ToastMetaContext = createContext<ToastRegionMeta<Record<string, unknown>> | null>(null);

/**
 * The `state`/`actions`/`meta` split `SKILL.md` §3.2 calls for: UI code
 * depends on this hook's return shape, never on `useToastManager` or
 * `createToastManager` directly, so the one place that would need to
 * change if the underlying engine ever did is this file.
 *
 * `Data` defaults to `Record<string, never>` — no fields at all — rather
 * than Base UI's own permissive `any` default. `shared` holds no domain
 * types (`COMPONENTS.md` §2); leaving the default open would let a Tier 3+
 * caller smuggle a domain-shaped payload through a Tier 0 toast without
 * this component ever having decided to allow it. A caller that genuinely
 * needs custom data instantiates `useToast<MyData>()` explicitly.
 */
export function useToast<Data extends object = Record<string, never>>(): {
    readonly state: ToastRegionState<Data>;
    readonly actions: ToastRegionActions<Data>;
    readonly meta: ToastRegionMeta<Data>;
} {
    const meta = use(ToastMetaContext);
    if (!meta) {
        throw new Error("useToast must be used inside <ToastRegion>");
    }
    const { toasts, add, close, update } = BaseToast.useToastManager<Data>();

    return {
        state: {
            toasts: toasts.map((toast) => ({
                id: toast.id,
                ...(toast.title === undefined ? {} : { title: toast.title }),
                ...(toast.description === undefined ? {} : { description: toast.description }),
                tone: (toast.type as ToastTone | undefined) ?? "neutral",
                ...(toast.data === undefined ? {} : { data: toast.data }),
            })),
        },
        actions: {
            add: (options) => add(toBaseOptions(options)),
            close,
            update: (id, options) => { update(id, toBaseOptions(options as AddToastOptions<Data>)); },
        },
        meta,
    };
}

/**
 * A placeholder glyph — real icons arrive once `Icon`'s own API is decided
 * (`COMPONENTS.md` §13). Kept private to this file, same reasoning as
 * `Popover.tsx`'s own copy.
 */
function DismissGlyph() {
    return (
        <svg width="12" height="12" viewBox="0 0 12 12" aria-hidden="true">
            <path d="M2 2l8 8M10 2l-8 8" stroke="currentColor" strokeWidth="1.5" fill="none" strokeLinecap="round" />
        </svg>
    );
}

/**
 * `bg-status-{tone}-subtle` mirrors `Badge`'s own planned tone treatment
 * (`COMPONENTS.md`'s Status and feedback row: "`tone` plus `solid`/`subtle`,
 * backed by the existing `statusBadge` component tokens") rather than a
 * fill deep enough to need `textOnSolid` — a toast is read at a glance
 * over the page behind it, not a solid button, so the same "wash, not
 * fill" treatment `Badge`'s own subtle variant will use fits it better
 * than `Button`'s `danger` tone's solid fill.
 */
const TONE_ACCENT_CLASS: Record<ToastTone, string> = {
    neutral: "border-border-subtle",
    info: "border-status-info",
    success: "border-status-success",
    danger: "border-status-danger",
    attention: "border-status-attention",
};

const ROOT_CLASS =
    "pointer-events-auto flex w-max items-start gap-inline-tight rounded-card border border-l-4 bg-surface-elevated p-stack text-body text-text-primary shadow-elevation2 outline-none transition-popover data-[starting-style]:translate-y-1 data-[starting-style]:opacity-0 data-[ending-style]:opacity-0";

function ToastItem<Data extends object>({ toast }: { readonly toast: BaseToast.Root.ToastObject<Data> }) {
    const tone = (toast.type as ToastTone | undefined) ?? "neutral";

    return (
        <BaseToast.Root toast={toast} className={[ROOT_CLASS, TONE_ACCENT_CLASS[tone]].join(" ")}>
            <Dot tone={tone} label={tone} className="mt-inline-tight" />
            {/*
             * `Title`/`Description`/`Action` take no content props of their
             * own — each reads straight from the `toast` on the surrounding
             * `Root` (verified by reading `ToastAction.js`: it resolves its
             * own `children` and `onClick` from `toast.actionProps`
             * internally). Passing `{...toast.actionProps}` here too was
             * this component's own first draft, and it double-fired `onClick`
             * — the same handler wired in twice by two independent prop
             * merges (Base UI's own, plus this one) — caught by a real
             * Chromium click producing two toasts from one, not assumed.
             */}
            <div className="flex flex-1 flex-col gap-inline-tight">
                {toast.title ? <BaseToast.Title className="text-body-strong text-text-primary" /> : null}
                {toast.description ? <BaseToast.Description className="text-small text-text-secondary" /> : null}
                {toast.actionProps ? (
                    <BaseToast.Action className="self-start text-body-strong text-interactive-primary-text underline transition-hover hover:opacity-80 focus-visible:focus-ring" />
                ) : null}
            </div>
            <BaseToast.Close
                render={
                    <IconButton label="Dismiss" tone="ghost" size="sm">
                        <DismissGlyph />
                    </IconButton>
                }
            />
        </BaseToast.Root>
    );
}

/**
 * `z-toast` — "above the modal on purpose: a toast confirming what just
 * happened inside a dialog is worthless if the dialog hides it"
 * (`semantic/z-index.ts`) — lives here on the Viewport, which is the fixed,
 * positioned element (verified by rendering; the same "the class has to
 * sit on whatever actually carries `position`" finding `Popover.tsx`'s own
 * `PopoverPopup` documents). `pointer-events-none` on the Viewport, undone
 * per-toast on `Toast.Root` (`ROOT_CLASS`'s own `pointer-events-auto`),
 * keeps the empty space around a corner-anchored stack from blocking clicks
 * on whatever the page renders underneath it.
 */
/**
 * `style`, not a `right-0 bottom-0` class pair: those read from Tailwind's
 * bare `--spacing` multiplier, which the adapter deliberately clears —
 * `0` compiles to nothing at all under it, the same finding
 * `Drawer.tsx`'s own `FULL_VIEWPORT_STYLE` documents (confirmed live there
 * by rendering, not re-verified independently here since it is the same
 * underlying adapter behaviour). Corner-pinning is geometry, not a spacing
 * decision, so an inline style is the honest fix rather than a token this
 * value has no real semantic content to name.
 */
const CORNER_STYLE = { position: "fixed", right: 0, bottom: 0 } as const;

function ToastViewportInternal() {
    const { toasts } = BaseToast.useToastManager();

    return (
        <BaseToast.Portal>
            <BaseToast.Viewport style={CORNER_STYLE} className="pointer-events-none z-toast flex flex-col gap-inline-tight p-stack">
                {toasts.map((toast) => (
                    <ToastItem key={toast.id} toast={toast} />
                ))}
            </BaseToast.Viewport>
        </BaseToast.Portal>
    );
}

export interface ToastRegionProps {
    /** The rest of the app — `ToastRegion` mounts once, near the app shell root, and wraps everything that might need to fire a toast. */
    readonly children: ReactNode;
    /** @default 5000 */
    readonly timeout?: number;
    /** @default 3 */
    readonly limit?: number;
    /**
     * A pre-created manager (`createToastManager()`), for a caller that
     * also needs to fire toasts from outside React (see `meta.manager`
     * above). Created internally, once, when omitted.
     */
    readonly manager?: ToastManager<Record<string, unknown>>;
}

/**
 * Tier 0 — the failure channel for optimistic writes, and the host of
 * undo. The region owns queueing: mount it once, and any descendant calls
 * `useToast().actions.add(...)` to enqueue a toast without knowing whether
 * the queue is local state, a global store, or (as it is here) Base UI's
 * own `ToastProvider` — exactly the provider-owns-the-implementation split
 * `SKILL.md` §3.2 describes. Behaviour (auto-dismiss timers, stacking and
 * expansion, swipe-to-dismiss, `aria-live` announcement at the priority the
 * caller sets, focus handling) is Base UI's own (`@base-ui/react/toast`,
 * ADR-031); this module supplies tokens, the `tone` vocabulary, and the
 * `state`/`actions`/`meta` seam on top of it.
 */
export function ToastRegion({ children, timeout, limit, manager: managerProp }: ToastRegionProps) {
    const [ownManager] = useState(() => BaseToast.createToastManager<Record<string, unknown>>());
    const manager = managerProp ?? ownManager;

    return (
        <ToastMetaContext value={{ manager }}>
            <BaseToast.Provider {...(timeout === undefined ? {} : { timeout })} {...(limit === undefined ? {} : { limit })} toastManager={manager}>
                {children}
                <ToastViewportInternal />
            </BaseToast.Provider>
        </ToastMetaContext>
    );
}
