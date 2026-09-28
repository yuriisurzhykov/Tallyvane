"use strict";

const SEVERITY_NUMBERS = {
    trace: 1,
    debug: 5,
    info: 9,
    warn: 13,
    error: 17,
    fatal: 21,
};

function createStdoutLogSink() {
    return { emit(scope, service, record) {
        process.stdout.write(
            `${JSON.stringify({
                timestamp: new Date().toISOString(),
                service,
                scope,
                severity: record.severity,
                event: record.event,
                body: record.body ?? record.event,
                attributes: {
                    "event.name": record.event,
                    ...(record.attributes ?? {}),
                },
            })}\n`,
        );
    } };
}

class OtlpLogSink {
    constructor() {
        const { context } = require("@opentelemetry/api");
        const { logs } = require("@opentelemetry/api-logs");
        this.context = context;
        this.logs = logs;
        this.loggers = new Map();
    }

    emit(scope, service, record) {
        let logger = this.loggers.get(scope);
        if (!logger) {
            logger = this.logs.getLogger(scope);
            this.loggers.set(scope, logger);
        }

        logger.emit({
            context: this.context.active(),
            severityNumber: SEVERITY_NUMBERS[record.severity],
            severityText: record.severity.toUpperCase(),
            body: record.body ?? record.event,
            attributes: {
                "event.name": record.event,
                ...(record.attributes ?? {}),
            },
        });
    }
}

function createLogger(scope, options = {}) {
    const env = options.env ?? process.env;
    const service = env.OTEL_SERVICE_NAME ?? scope;
    const exporters = env.OTEL_LOGS_EXPORTER?.split(",").map((value) => value.trim()) ?? [];
    const useOtlp = env.OTEL_SDK_DISABLED?.toLowerCase() !== "true" && exporters.includes("otlp");
    const sink = useOtlp
        ? (options.sinks?.otlp ?? new OtlpLogSink())
        : (options.sinks?.console ?? createStdoutLogSink());

    return Object.freeze({
        emit(record) {
            sink.emit(scope, service, record);
        },
    });
}

module.exports = { createLogger };
