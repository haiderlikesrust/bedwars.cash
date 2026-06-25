# ---- Build BedWarsCash plugin (Gradle on JDK 23, compile with JDK 25 toolchain) ----
FROM eclipse-temurin:23-jdk-jammy AS plugin

ENV JAVA_HOME=/opt/java/openjdk
WORKDIR /build
COPY plugins/bedwarscash/ ./
RUN sed -i 's/\r$//' gradlew \
    && chmod +x gradlew \
    && ./gradlew build --no-daemon -x test

# ---- Paper 26.1.2 game server ----
FROM eclipse-temurin:25-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends bash curl ca-certificates python3 \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /minecraft

COPY docker/minecraft/entrypoint.sh /usr/local/bin/bwc-entrypoint.sh
COPY docker/minecraft/config/ /opt/bwc-defaults/
COPY docker/minecraft/config/BedWarsCash/ /opt/bwc-defaults/BedWarsCash/

COPY --from=plugin /build/build/libs/bedwarscash-*.jar /opt/BedWarsCash.jar

RUN chmod +x /usr/local/bin/bwc-entrypoint.sh \
    && mkdir -p plugins \
    && cp /opt/BedWarsCash.jar plugins/BedWarsCash.jar

ENV PAPER_VERSION=26.1.2
ENV MC_MEMORY=4G

EXPOSE 25565

VOLUME ["/minecraft"]

ENTRYPOINT ["/usr/local/bin/bwc-entrypoint.sh"]
