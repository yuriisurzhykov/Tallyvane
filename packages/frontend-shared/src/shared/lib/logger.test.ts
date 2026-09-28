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

function sinks() {
    const consoleEmit = vi.fn<LogSink["emit"]>();
    const otlpEmit = vi.fn<LogSink["emit"]>();
    return {
        console: { emit: consoleEmit },
        otlp: { emit: otlpEmit },
        consoleEmit,
        otlpEmit,
    };
}

describe("the shared logger contract", () => {
    it("routes local records to stdout when OpenTelemetry is disabled", () => {
        const outputs = sinks();
        const logger = createLogger("http.server.access", {
            env: {
                OTEL_SDK_DISABLED: "true",
                OTEL_LOGS_EXPORTER: "otlp",
                OTEL_SERVICE_NAME: "tallyvane-frontend-app",
            },
            sinks: outputs,
        });

        logger.emit(record);

        expect(outputs.consoleEmit).toHaveBeenCalledWith("http.server.access", "tallyvane-frontend-app", record);
        expect(outputs.otlpEmit).not.toHaveBeenCalled();
    });

    it("routes production records to the configured OTLP implementation", () => {
        const outputs = sinks();
        const logger = createLogger("http.server.access", {
            env: { OTEL_LOGS_EXPORTER: "otlp", OTEL_SERVICE_NAME: "tallyvane-frontend-admin" },
            sinks: outputs,
        });

        logger.emit(record);

        expect(outputs.otlpEmit).toHaveBeenCalledWith("http.server.access", "tallyvane-frontend-admin", record);
        expect(outputs.consoleEmit).not.toHaveBeenCalled();
    });

    it("uses stdout when no remote log exporter is configured", () => {
        const outputs = sinks();
        const logger = createLogger("http.server.access", {
            env: { OTEL_LOGS_EXPORTER: "none", OTEL_SERVICE_NAME: "tallyvane-frontend-web" },
            sinks: outputs,
        });

        logger.emit(record);

        expect(outputs.consoleEmit).toHaveBeenCalledWith("http.server.access", "tallyvane-frontend-web", record);
        expect(outputs.otlpEmit).not.toHaveBeenCalled();
    });
});
