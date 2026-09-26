import { describe, expect, it, vi } from "vitest";
import { createLogger, type LogRecord, type LogSink } from "./logger.cjs";

const record: LogRecord = {
    severity: "info",
    event: "http.server.request",
    attributes: {
        "http.request.method": "GET",
        "url.path": "/today",
        "http.response.status_code": 200,
    },
};

function sinks(): { console: LogSink; otlp: LogSink } {
    return {
        console: { emit: vi.fn() },
        otlp: { emit: vi.fn() },
    };
}

describe("the shared logger contract", () => {
    it("routes local records to stdout when OpenTelemetry is disabled", () => {
        const outputs = sinks();
        const logger = createLogger("tallyvane-frontend-app", {
            env: { OTEL_SDK_DISABLED: "true", OTEL_LOGS_EXPORTER: "otlp" },
            sinks: outputs,
        });

        logger.emit(record);

        expect(outputs.console.emit).toHaveBeenCalledWith("tallyvane-frontend-app", record);
        expect(outputs.otlp.emit).not.toHaveBeenCalled();
    });

    it("routes production records to the configured OTLP implementation", () => {
        const outputs = sinks();
        const logger = createLogger("tallyvane-frontend-admin", {
            env: { OTEL_LOGS_EXPORTER: "otlp" },
            sinks: outputs,
        });

        logger.emit(record);

        expect(outputs.otlp.emit).toHaveBeenCalledWith("tallyvane-frontend-admin", record);
        expect(outputs.console.emit).not.toHaveBeenCalled();
    });

    it("uses stdout when no remote log exporter is configured", () => {
        const outputs = sinks();
        const logger = createLogger("tallyvane-frontend-web", {
            env: { OTEL_LOGS_EXPORTER: "none" },
            sinks: outputs,
        });

        logger.emit(record);

        expect(outputs.console.emit).toHaveBeenCalledWith("tallyvane-frontend-web", record);
        expect(outputs.otlp.emit).not.toHaveBeenCalled();
    });
});
