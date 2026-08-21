#!/usr/bin/env node
/**
 * Kills whatever is listening on the given TCP ports, if anything is. Exists
 * because this repo's own `webServer` configs reuse an already-running local
 * server rather than restarting it (`reuseExistingServer: !process.env.CI` —
 * fast iteration, on purpose), which is exactly what turns an ad-hoc debug
 * server (started by hand, outside any `pnpm run test:*` script, to poke at a
 * story in a browser) into an orphan the next `pnpm run test:*` invocation
 * never notices and never cleans up — found the hard way running one too many
 * manual `http-server` instances side by side while debugging a contrast
 * failure live.
 *
 * By port, not by process name or command-line text: a name/command match
 * risks catching something it should never touch (an editor's own language
 * server, say) on a machine whose exact process list this script cannot
 * predict. A port is unambiguous — if this repo's own tooling does not own
 * whatever is bound to one of its own known ports, something else is already
 * wrong on the machine that killing a process here would not fix anyway.
 *
 * Usage: node free-ports.mjs <port> [<port> ...]
 * Exits 0 whether or not anything was actually listening — freeing an
 * already-free port is success, not a no-op to warn about.
 */
import { execFileSync } from "node:child_process";

const ports = process.argv.slice(2).map(Number);

if (ports.length === 0 || ports.some((port) => !Number.isInteger(port) || port <= 0)) {
    console.error("Usage: node free-ports.mjs <port> [<port> ...]");
    process.exit(1);
}

/** Every helper below returns the OS process IDs bound to one port, or an empty array — never throws for "nothing found," which is the expected common case. */
function pidsOnPort(port) {
    try {
        if (process.platform === "win32") {
            return execFileSync(
                "powershell.exe",
                [
                    "-NoProfile",
                    "-Command",
                    `Get-NetTCPConnection -LocalPort ${port} -State Listen -ErrorAction SilentlyContinue | Select-Object -ExpandProperty OwningProcess -Unique`,
                ],
                { encoding: "utf-8" },
            )
                .split(/\s+/)
                .filter(Boolean)
                .map(Number);
        }
        // macOS/Linux: `lsof` is the standard tool for exactly this question.
        return execFileSync("lsof", ["-ti", `:${port}`], { encoding: "utf-8" })
            .split(/\s+/)
            .filter(Boolean)
            .map(Number);
    } catch {
        // A non-zero exit from `lsof`/PowerShell here means "nothing matched," not a real error.
        return [];
    }
}

function describe(pid) {
    try {
        if (process.platform === "win32") {
            return execFileSync(
                "powershell.exe",
                ["-NoProfile", "-Command", `(Get-CimInstance Win32_Process -Filter "ProcessId=${pid}").CommandLine`],
                { encoding: "utf-8" },
            ).trim();
        }
        return execFileSync("ps", ["-p", String(pid), "-o", "command="], { encoding: "utf-8" }).trim();
    } catch {
        return "(process already gone)";
    }
}

function kill(pid) {
    try {
        if (process.platform === "win32") {
            execFileSync("powershell.exe", ["-NoProfile", "-Command", `Stop-Process -Id ${pid} -Force -ErrorAction SilentlyContinue`]);
        } else {
            process.kill(pid, "SIGKILL");
        }
        return true;
    } catch {
        return false;
    }
}

let killedAny = false;
for (const port of ports) {
    for (const pid of new Set(pidsOnPort(port))) {
        const command = describe(pid);
        const ok = kill(pid);
        console.log(`${ok ? "killed" : "failed to kill"} pid ${pid} on port ${port} — ${command}`);
        killedAny ||= ok;
    }
}

if (!killedAny) {
    console.log(`ports already free: ${ports.join(", ")}`);
}
