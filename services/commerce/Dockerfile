FROM oraclelinux:9-slim@sha256:2aec3940ecce6e008c59040c9e62945ad6ecb810ce893253e216b49732546132 AS build
ARG TARGETARCH
ARG MAVEN_VERSION=3.10.0
RUN microdnf install -y tar gzip unzip findutils && microdnf clean all
RUN case "$TARGETARCH" in \
      arm64) archive_arch=aarch64; archive_sha=fcf80d778fde63110d4856d2518f29da72944f4772a6041270887955e4196d92 ;; \
      amd64) archive_arch=x64; archive_sha=6deafad80454d1f968105b1f582d89b8c2e88a5dfc98116a8226e0ec2fd5df8e ;; \
      *) exit 1 ;; \
    esac && \
    curl --fail --location --max-time 300 "https://download.oracle.com/java/25/archive/jdk-25.0.5.0.3_linux-${archive_arch}_bin.tar.gz" -o /tmp/jdk.tar.gz && \
    printf '%s  /tmp/jdk.tar.gz\n' "$archive_sha" | sha256sum -c - && \
    mkdir -p /opt/jdk && tar -xzf /tmp/jdk.tar.gz --strip-components=1 -C /opt/jdk && rm /tmp/jdk.tar.gz
RUN curl --fail --location --max-time 300 "https://repo.maven.apache.org/maven2/org/apache/maven/apache-maven/${MAVEN_VERSION}/apache-maven-${MAVEN_VERSION}-bin.zip" -o /tmp/maven.zip && \
    printf '22d31676d5b92ed53308c19ecded3245d0efd0cddfec51ec7bab62f4f13ad70ddddceff8e62940e0e8e2421f3cde8106737f4becec0aa034ffeff2a754e88337  /tmp/maven.zip\n' | sha512sum -c - && \
    unzip -q /tmp/maven.zip -d /opt && mv /opt/apache-maven-${MAVEN_VERSION} /opt/maven && rm /tmp/maven.zip
ENV JAVA_HOME=/opt/jdk
ENV MAVEN_HOME=/opt/maven
ENV PATH=/opt/jdk/bin:/opt/maven/bin:$PATH
ENV MAVEN_USER_HOME=/workspace/.build/maven
WORKDIR /workspace
COPY .mvn .mvn
COPY pom.xml ./
COPY shared shared
COPY contracts/security contracts/security
COPY services/commerce services/commerce
COPY services/experience services/experience
COPY services/integration services/integration
COPY scripts/java scripts/java
COPY database/changelog database/changelog
# Cache pinned downloads once per architecture; keep compiled outputs outside the cache.
# Both Maven calls hold the cache locks, so owner artifacts cannot interleave across builds.
RUN --mount=type=cache,id=nigson-maven-repository-${TARGETARCH},target=/workspace/.build/m2,sharing=locked \
find /workspace/.build/m2 -path "*/com/nigson/*" -name maven-metadata-local.xml -delete && \
    mvn -B install -DskipTests && \
    mvn -B -pl services/commerce dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=/migration-lib
RUN javac -cp '/migration-lib/*' -d /tools scripts/java/MigrationTargetPolicy.java scripts/java/OwnerMigration.java && javac -d /probe scripts/java/HealthProbe.java
RUN jlink --module-path /opt/jdk/jmods --add-modules java.se,jdk.net,jdk.unsupported,jdk.crypto.ec,jdk.management,jdk.zipfs --strip-java-debug-attributes --no-man-pages --no-header-files --output /jre

FROM oraclelinux:9-slim@sha256:2aec3940ecce6e008c59040c9e62945ad6ecb810ce893253e216b49732546132 AS runtime-base
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
COPY --from=build /workspace/services/commerce/target/commerce-0.1.0.jar /app/application.jar
ENV SERVER_PORT=8081
EXPOSE 8081
HEALTHCHECK --interval=10s --timeout=5s --start-period=30s --retries=12 CMD ["java", "-cp", "/app/probe", "HealthProbe"]
ENTRYPOINT ["java", "-jar", "/app/application.jar"]
