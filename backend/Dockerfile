# The backend image: the HTTP server and the migration command, in one image.
#
# It compiles nothing. The distributions have to exist before this is built:
#
#     ./gradlew :server:installDist :migrate:installDist
#
# That split is deliberate. Compiling here would mean a JDK and a Gradle cache inside the
# build, which is slower and duplicates caching that CI already does for the test run.

# The OpenTelemetry Java agent (ADR-067): a downloaded binary, not a dependency any module
# compiles against, so it gets a build stage of its own and a checksum rather than trust in the
# network path that fetched it. The hash was computed once, from this exact release's asset —
# aquasecurity/trivy-action's own supply-chain compromise (2026-03-19, GHSA-69fq-xp46-6x23,
# scan-image/action.yml's own comment has the story) is the reason a third-party binary this
# image ships is never trusted on the strength of a version tag alone.
FROM alpine:3.22 AS otel-agent
ARG OTEL_JAVAAGENT_VERSION=v2.31.1
ARG OTEL_JAVAAGENT_SHA256=bbf83c151b6400709e2f225bdd07a04f839d9d13b8b93464241333fd25d3e3ba
RUN apk add --no-cache curl \
    && curl --fail --location --silent --show-error \
      --output /tmp/opentelemetry-javaagent.jar \
      "https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/${OTEL_JAVAAGENT_VERSION}/opentelemetry-javaagent.jar" \
    && echo "${OTEL_JAVAAGENT_SHA256}  /tmp/opentelemetry-javaagent.jar" | sha256sum -c -

FROM eclipse-temurin:21-jre

# Unused, and vulnerable as shipped: this base image's Canonical rock layer includes Pebble
# (a service-manager binary, github.com/canonical/pebble) that Adoptium's own entrypoint
# never calls — confirmed against the image's real build history via the registry API, not
# assumed from the file merely existing: Ubuntu's own rock build clears entrypoint/cmd right
# after adding that layer, and Adoptium sets its own (`/__cacert_entrypoint.sh`). Trivy
# flagged its embedded Go stdlib directly (CVE-2026-33818 and seven siblings, 2026-08-28);
# deleting the file removes the surface rather than a component this image has any use for.
RUN rm -f /usr/bin/pebble

# Two trees, deliberately not merged into one lib/. Measured 2026-08-27: the two dependency
# graphs resolve kotlin-reflect to 2.3.21 for the server and 2.3.20 for migrations, so a single
# classpath would carry both and which one won would depend on ordering.
WORKDIR /opt/tallyvane
COPY server/build/install/server server
COPY migrate/build/install/migrate migrate
COPY --from=otel-agent /tmp/opentelemetry-javaagent.jar opentelemetry-javaagent.jar

# The image ships with a root account it has no use for. Same reasoning as the tunnel's
# credentials file: the process gets what it needs to run and nothing else.
RUN useradd --system --no-create-home --shell /usr/sbin/nologin tallyvane \
  && chown -R root:root /opt/tallyvane
USER tallyvane

# Documentation for a reader; publishing a port is compose's business, and this deployment
# publishes none — nginx reaches the container over the docker network.
EXPOSE 8080

# `java` directly, not the bin/server script Gradle generates. That script is bash, so calling
# it would tie this image to a base that has a shell; this form runs on any of them, which keeps
# the choice of base image a one-line decision rather than a rewrite.
#
# The `*` is expanded by the JVM, not by a shell — a wildcard classpath entry is a Java
# feature. Safe within one tree: `server/lib` holds exactly one kotlin-reflect.
#
# No heap setting here on purpose. A JVM reads the container's memory limit by itself and takes
# 25% of it, which is a conservative default and a safe one. Raising that to the 66% §16.2
# implies is done with JAVA_TOOL_OPTIONS in docker-compose.yml, in the same block as the limit
# it is a percentage of — because measured, 66% with no limit present means 66% of the whole
# machine: 4.9 GiB on the machine this was measured on. Keeping the two together makes that
# combination impossible rather than merely warned about.
CMD ["java", "-cp", "/opt/tallyvane/server/lib/*", "tallyvane.server.Application"]
