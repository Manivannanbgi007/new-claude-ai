# ---------------------------------------------------------------
# Kafka Connect = Confluent image (includes the Avro converter)
#               + the Debezium MySQL connector plugin.
# Built automatically by "docker compose up -d --build kafka-connect".
# ---------------------------------------------------------------
ARG CONFLUENT_VERSION=8.0.0
ARG DEBEZIUM_VERSION=3.2.0.Final

# Stage 1: download and unpack the Debezium plugin (official archive on Maven Central)
FROM alpine:3.20 AS debezium
ARG DEBEZIUM_VERSION
RUN apk add --no-cache curl \
 && mkdir -p /plugins \
 && curl -fsSL "https://repo1.maven.org/maven2/io/debezium/debezium-connector-mysql/${DEBEZIUM_VERSION}/debezium-connector-mysql-${DEBEZIUM_VERSION}-plugin.tar.gz" \
    | tar -xz -C /plugins

# Stage 2: Confluent Kafka Connect with the plugin copied into its plugin path
FROM confluentinc/cp-kafka-connect:${CONFLUENT_VERSION}
COPY --from=debezium /plugins /usr/share/confluent-hub-components
