"use client";

import { useState } from "react";
import { useApi } from "frontend-shared/api";
import { SignIns, type CodeKind, type CodeOffer, type CodeReaction } from "@/entities/sign-in";
import { TypedCode } from "./TypedCode";
import { usePause } from "./usePause";

export type Mistake = "none" | "wrong" | "failed";

export interface CodeEntryCallbacks {
    /** The code was right. `codesLeft` is told when it was a recovery code. */
    readonly onAccepted: (codesLeft: number | undefined) => void;
    readonly onOver: () => void;
    readonly onChanged: () => void;
    /** Words for the screen reader when a pause begins. */
    readonly announce: (seconds: number) => string;
}

export interface CodeEntry {
    readonly kind: CodeKind;
    readonly other: CodeKind;
    readonly text: string;
    readonly working: boolean;
    readonly mistake: Mistake;
    readonly secondsLeft: number;
    readonly announcement: string;
    readonly canSend: boolean;
    readonly type: (text: string) => void;
    readonly send: () => void;
    readonly switchTo: (kind: CodeKind) => void;
}

/**
 * What is typed, what is being waited for, and what the server said, for the field of the second step.
 * Nothing short of a whole code is sent, and a pause the server asked for blocks sending until it is over.
 */
export function useCodeEntry(offer: CodeOffer, callbacks: CodeEntryCallbacks): CodeEntry {
    const api = useApi();
    const pause = usePause(offer.pause());
    const [kind, setKind] = useState<CodeKind>(offer.firstKind());
    const [text, setText] = useState("");
    const [working, setWorking] = useState(false);
    const [mistake, setMistake] = useState<Mistake>("none");
    const [announcement, setAnnouncement] = useState("");

    const typed = new TypedCode(text);
    const canSend = typed.isComplete(kind) && !working && pause.secondsLeft === 0;

    const wait = (seconds: number) => {
        pause.start(seconds);
        setAnnouncement(callbacks.announce(seconds));
    };

    const reaction: CodeReaction<void> = {
        accepted: callbacks.onAccepted,
        wrong: (seconds) => {
            setMistake("wrong");
            setText("");
            setWorking(false);
            if (seconds > 0) wait(seconds);
        },
        paused: (seconds) => {
            setText("");
            setWorking(false);
            wait(seconds);
        },
        over: callbacks.onOver,
        changed: callbacks.onChanged,
    };

    const send = () => {
        if (!canSend) {
            return;
        }
        setWorking(true);
        setMistake("none");
        new SignIns(api).answer(kind, typed.forKind(kind)).then(
            (answer) => { answer.when(reaction); },
            () => {
                setMistake("failed");
                setWorking(false);
            },
        );
    };

    /** Typing clears a "wrong" mistake: the field stays invalid until then, and an invalid field cannot be submitted. */
    const type = (next: string) => {
        setText(next);
        if (mistake === "wrong") setMistake("none");
    };

    const switchTo = (next: CodeKind) => {
        setKind(next);
        setText("");
        setMistake("none");
    };

    return {
        kind,
        other: kind === "totp" ? "recovery_code" : "totp",
        text,
        working,
        mistake,
        secondsLeft: pause.secondsLeft,
        announcement,
        canSend,
        type,
        send,
        switchTo,
    };
}
