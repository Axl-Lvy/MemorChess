# Builds and runs the :server module (proj.memorchess.axl.server.MainKt), the sync backend.
#
# Two stages: the build stage runs the ordinary Gradle build (configuring the whole
# multi-project build, including the Android modules, is harmless here — they only warn about
# the missing Android SDK, they don't fail), then the runtime stage keeps just the installed
# distribution on a JRE, not a JDK.
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Short git sha this image was built from, baked into build-info.properties and served over
# GET /v1/version. Defaults to "dev" for a manual `docker build` with no --build-arg.
ARG BUILD_SHA=dev

COPY . .
RUN ./gradlew :server:installDist -PbuildSha="${BUILD_SHA}" --no-daemon --console=plain

FROM eclipse-temurin:21-jre AS runtime

# Fixed numeric uid/gid: no shell, no login, nothing a compromise can use beyond what the app
# process itself already has.
RUN groupadd --gid 10001 chess \
  && useradd --uid 10001 --gid chess --no-create-home --shell /usr/sbin/nologin chess

COPY --from=build --chown=chess:chess /workspace/server/build/install/server /app

USER chess
WORKDIR /app

# The JVM is container-aware by default (respects a cgroup memory limit), but its default
# MaxRAMPercentage (25%) is tuned for a JVM sharing the box with other processes. This container
# runs nothing else, so give it most of whatever `mem_limit` the deployment sets. Override via
# JAVA_TOOL_OPTIONS if a deployment needs something else.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# Config comes entirely from the environment; see ServerConfig.kt (SYNC_DB_URL, SYNC_DB_USER,
# SYNC_DB_PASSWORD, SYNC_JWT_ISSUER, SYNC_JWT_AUDIENCE, SYNC_JWKS_URL, optional SYNC_PORT).
EXPOSE 8080
ENTRYPOINT ["/app/bin/server"]
