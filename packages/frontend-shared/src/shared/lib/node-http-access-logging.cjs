"use strict";

const http = require("node:http");
const { createLogger } = require("./logger.cjs");

const logger = createLogger("tallyvane.http.access");
const originalEmit = http.Server.prototype.emit;

if (!originalEmit.tallyvaneAccessLogging) {
    const instrumentedEmit = function (event, ...args) {
        if (event === "request" && args.length >= 2) {
            const [request, response] = args;
            const startedAt = process.hrtime.bigint();
            let completed = false;

            const complete = () => {
                if (completed) return;
                completed = true;

                const status = response.statusCode;
                const severity = status >= 500 ? "error" : status >= 400 ? "warn" : "info";
                const path = typeof request.url === "string" ? request.url.split("?", 1)[0] || "/" : "/";
                const durationMs = Number((Number(process.hrtime.bigint() - startedAt) / 1_000_000).toFixed(3));

                logger.emit({
                    severity,
                    event: "http.server.request",
                    attributes: {
                        "http.request.method": request.method ?? "UNKNOWN",
                        "url.path": path,
                        "http.response.status_code": status,
                        "http.server.duration_ms": durationMs,
                    },
                });
            };

            response.once("finish", complete);
            response.once("close", complete);
        }

        return originalEmit.call(this, event, ...args);
    };
    instrumentedEmit.tallyvaneAccessLogging = true;
    http.Server.prototype.emit = instrumentedEmit;
}
