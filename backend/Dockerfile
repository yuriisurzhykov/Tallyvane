# The backend image: the HTTP server and the migration command, in one image.
#
# It compiles nothing. The distributions have to exist before this is built:
#
#     ./gradlew :app:installDist :migrate:installDist
#
# That split is deliberate. Compiling here would mean a JDK and a Gradle cache inside the
# build, which is slower and duplicates caching that CI already does for the test run.
FROM eclipse-temurin:21-jre

# Two trees, deliberately not merged into one lib/. Measured 2026-08-27: the two dependency
# graphs resolve kotlin-reflect to 2.3.21 for the server and 2.3.20 for migrations, so a single
# classpath would carry both and which one won would depend on ordering.
WORKDIR /opt/tallyvane
COPY app/build/install/app app
COPY migrate/build/install/migrate migrate

# The image ships with a root account it has no use for. Same reasoning as the tunnel's
# credentials file: the process gets what it needs to run and nothing else.
RUN useradd --system --no-create-home --shell /usr/sbin/nologin tallyvane \
  && chown -R root:root /opt/tallyvane
USER tallyvane

# Documentation for a reader; publishing a port is compose's business, and this deployment
# publishes none — nginx reaches the container over the docker network.
EXPOSE 8080

# `java` directly, not the bin/app script Gradle generates. That script is bash, so calling it
# would tie this image to a base that has a shell; this form runs on any of them, which keeps
# the choice of base image a one-line decision rather than a rewrite.
#
# The `*` is expanded by the JVM, not by a shell — a wildcard classpath entry is a Java
# feature. Safe within one tree: `app/lib` holds exactly one kotlin-reflect.
#
# No heap setting here on purpose. A JVM reads the container's memory limit by itself and takes
# 25% of it, which is a conservative default and a safe one. Raising that to the 66% §16.2
# implies is done with JAVA_TOOL_OPTIONS in docker-compose.yml, in the same block as the limit
# it is a percentage of — because measured, 66% with no limit present means 66% of the whole
# machine: 4.9 GiB on the machine this was measured on. Keeping the two together makes that
# combination impossible rather than merely warned about.
CMD ["java", "-cp", "/opt/tallyvane/app/lib/*", "tallyvane.app.Application"]
