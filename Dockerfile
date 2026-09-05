# Builds and runs the :server module (proj.memorchess.axl.server.MainKt), the sync backend.
#
# Two stages: the build stage runs the ordinary Gradle build. Configuring the whole
# multi-project build, including the Android modules, is harmless here: they only warn about
# the missing Android SDK, they don't fail. The runtime stage then keeps just the installed
# distribution on a JRE, not a JDK.
FROM eclipse-temurin:25-jdk AS build
WORKDIR /workspace

# Short git sha this image was built from, baked into build-info.properties and served over
# GET /v1/version. Defaults to "dev" for a manual `docker build` with no --build-arg.
ARG BUILD_SHA=dev

COPY . .

# The wasmJs build downloads its own Node toolchain, whose binary needs libatomic.so.1. This base
# image doesn't ship it (unlike a typical CI runner image, which is why check.yml's wasm-tests job
# never needed this).
RUN apt-get update && apt-get install -y --no-install-recommends libatomic1 \
  && rm -rf /var/lib/apt/lists/*

RUN ./gradlew :server:installDist :composeApp:wasmJsBrowserDistribution -PbuildSha="${BUILD_SHA}" --no-daemon --console=plain

FROM eclipse-temurin:25-jre AS runtime

# wget: unlike the 21-jre base this replaced, 25-jre's Ubuntu 26.04 base doesn't ship it, and
# hosts/minipc/docker-compose.apps.yml's healthcheck (CMD wget ... /health) depends on it.
RUN apt-get update && apt-get install -y --no-install-recommends wget \
  && rm -rf /var/lib/apt/lists/*

# Fixed numeric uid/gid: no shell, no login, nothing a compromise can use beyond what the app
# process itself already has.
RUN groupadd --gid 10001 chess \
  && useradd --uid 10001 --gid chess --no-create-home --shell /usr/sbin/nologin chess

COPY --from=build --chown=root:root /workspace/server/build/install/server /app

# The compiled wasmJs frontend bundle, served by the server itself (see SYNC_STATIC_DIR below).
# composeApp.js.map is dropped: it's source-map data with no runtime value in production.
COPY --from=build --chown=root:root /workspace/composeApp/build/dist/wasmJs/productionExecutable /app/www
RUN rm -f /app/www/composeApp.js.map

USER chess
WORKDIR /app

# The JVM is container-aware by default (respects a cgroup memory limit), but its default
# MaxRAMPercentage (25%) is tuned for a JVM sharing the box with other processes. This container
# runs nothing else, so give it most of whatever `mem_limit` the deployment sets. Override via
# JAVA_TOOL_OPTIONS if a deployment needs something else.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75"

# Fixed image layout, not a per-deployment value: baked in here rather than left to a deployment's
# own environment, unlike every other SYNC_* variable.
ENV SYNC_STATIC_DIR="/app/www"

# Config comes entirely from the environment. See ServerConfig.kt (SYNC_DB_URL, SYNC_DB_USER,
# SYNC_DB_PASSWORD, SYNC_JWT_ISSUER, SYNC_JWT_AUDIENCE, SYNC_JWKS_URL, optional SYNC_PORT).
EXPOSE 8080
ENTRYPOINT ["/app/bin/server"]
