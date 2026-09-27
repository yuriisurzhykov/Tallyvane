export type LogSeverity = "trace" | "debug" | "info" | "warn" | "error" | "fatal";

export interface LogRecord {
    severity: LogSeverity;
    event: string;
    body?: string;
    attributes?: Readonly<Record<string, string | number | boolean>>;
}

export interface LogSink {
    emit(scope: string, service: string, record: LogRecord): void;
}

export interface Logger {
    emit(record: LogRecord): void;
}

export interface LoggerOptions {
    env?: Readonly<Record<string, string | undefined>>;
    sinks?: {
        console: LogSink;
        otlp: LogSink;
    };
}

export declare function createLogger(service: string, options?: LoggerOptions): Logger;
