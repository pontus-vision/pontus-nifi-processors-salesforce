FROM maven:3.6-jdk-8-alpine as builder

COPY ./force-rest-api/pom.xml /pontus-nifi-processors-salesforce/force-rest-api/
COPY ./nifi-salesforce-api/pom.xml /pontus-nifi-processors-salesforce/nifi-salesforce-api/
COPY ./nifi-salesforce-controllerservice/pom.xml /pontus-nifi-processors-salesforce/nifi-salesforce-controllerservice/
COPY ./nifi-salesforce-nar/pom.xml /pontus-nifi-processors-salesforce/nifi-salesforce-nar/
COPY ./nifi-salesforce-processors/pom.xml /pontus-nifi-processors-salesforce/nifi-salesforce-processors/
COPY ./pom.xml /pontus-nifi-processors-salesforce/

RUN cd /pontus-nifi-processors-salesforce/ && \
    mvn -Dorg.slf4j.simpleLogger.log.org.apache.maven.cli.transfer.Slf4jMavenTransferListener=warn -B verify --fail-never

COPY . /pontus-nifi-processors-salesforce/

RUN cd pontus-nifi-processors-salesforce && \
    mvn --no-transfer-progress clean package -U -DskipTests

FROM scratch
COPY --from=builder /pontus-nifi-processors-salesforce/*/target/*nar /opt/nifi/nifi-current/lib/

