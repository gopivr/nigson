FROM oraclelinux:9-slim@sha256:fdf4581abc841d49f63cf456ea92f8152a80b5bdc62d5409a4e77827b824265c AS build
ARG TARGETARCH
RUN microdnf install -y tar gzip unzip findutils && microdnf clean all
RUN case "$TARGETARCH" in \
      arm64) archive_arch=aarch64; archive_sha=1aaf2d46506ecdf15569d5d3f0c2295a7c18795ec7a6ee030cf19406daccd0dc ;; \
      amd64) archive_arch=x64; archive_sha=505fdcb1f172b4aad23415f0584912cff90b7d902adc5f1593894b4a8cbf7c39 ;; \
      *) exit 1 ;; \
    esac && \
    curl --fail --location --max-time 300 "https://download.oracle.com/java/25/archive/jdk-25.0.2_linux-${archive_arch}_bin.tar.gz" -o /tmp/jdk.tar.gz && \
    printf '%s  /tmp/jdk.tar.gz\n' "$archive_sha" | sha256sum -c - && \
    mkdir -p /opt/jdk && tar -xzf /tmp/jdk.tar.gz --strip-components=1 -C /opt/jdk && rm /tmp/jdk.tar.gz
ENV JAVA_HOME=/opt/jdk
ENV PATH=/opt/jdk/bin:$PATH
ENV MAVEN_USER_HOME=/workspace/.build/maven
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY shared shared
COPY contracts/security contracts/security
COPY services/commerce services/commerce
COPY services/experience services/experience
COPY services/integration services/integration
COPY scripts/java scripts/java
COPY database/changelog database/changelog
RUN ./mvnw -B install
RUN ./mvnw -B -pl services/commerce dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=/migration-lib
RUN javac -cp '/migration-lib/*' -d /tools scripts/java/OwnerMigration.java && javac -d /probe scripts/java/HealthProbe.java
RUN jlink --module-path /opt/jdk/jmods --add-modules java.se,jdk.net,jdk.unsupported,jdk.crypto.ec,jdk.management,jdk.zipfs --strip-java-debug-attributes --no-man-pages --no-header-files --output /jre

FROM oraclelinux:9-slim@sha256:fdf4581abc841d49f63cf456ea92f8152a80b5bdc62d5409a4e77827b824265c AS runtime-base
COPY --from=build /jre /opt/java
ENV PATH=/opt/java/bin:$PATH
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=70 -Djava.io.tmpdir=/tmp"
WORKDIR /app
USER 10001:10001

FROM runtime-base AS migration
COPY --from=build /migration-lib /app/lib
COPY --from=build /tools /app/tools
COPY database/changelog /app/source/database/changelog
COPY services/commerce/src/main/resources/db/changelog /app/source/services/commerce/src/main/resources/db/changelog
COPY services/experience/src/main/resources/db/changelog /app/source/services/experience/src/main/resources/db/changelog
COPY services/integration/src/main/resources/db/changelog /app/source/services/integration/src/main/resources/db/changelog
ENV MIGRATION_ROOT=/app/source
ENTRYPOINT ["java", "-cp", "/app/tools:/app/lib/*", "OwnerMigration"]

FROM runtime-base AS application
COPY --from=build /probe /app/probe
COPY --from=build /workspace/services/experience/target/experience-0.1.0.jar /app/application.jar
ENV SERVER_PORT=8082
EXPOSE 8082
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=12 CMD ["java", "-cp", "/app/probe", "HealthProbe"]
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
