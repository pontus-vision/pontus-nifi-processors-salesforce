FROM maven:3.6-jdk-8-alpine as builder
COPY . /pontus-nifi-processors-salesforce/

RUN cd pontus-nifi-processors-salesforce && \
    mvn --no-transfer-progress clean package -U -DskipTests

FROM scratch
COPY --from=builder /pontus-nifi-processors-salesforce/*/target/*nar /opt/nifi/nifi-current/lib/

