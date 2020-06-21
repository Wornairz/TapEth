FROM maven:latest
ENV PATH /opt/kafka/bin:$PATH
ENV KAFKA_DIR "/opt/kafka"
ENV ZOOKEEPER_SERVER "10.0.100.22:2181"
ENV ETH_KAFKA_CONNECTOR "ETH Kafka Connector"
ENV ETH_CONNECT_DIR "/opt/connector"
ENV KAFKA_PLUGINS_DIR "/usr/local/share/kafka/plugins/"

ARG KAFKA_VERSION="2.12-2.5.0"

ADD kafka_${KAFKA_VERSION}.tgz /opt
RUN ln -s /opt/kafka_${KAFKA_VERSION} ${KAFKA_DIR} 

COPY eth-source-connector.properties ${KAFKA_DIR}/conf/
COPY worker.properties ${KAFKA_DIR}/conf/

COPY ${ETH_KAFKA_CONNECTOR}/ ${ETH_CONNECT_DIR}/
WORKDIR ${ETH_CONNECT_DIR}/
RUN mvn package
RUN mkdir -p ${KAFKA_PLUGINS_DIR}
RUN cp target/eth-connector-kafka-* ${KAFKA_PLUGINS_DIR}

COPY kafka-starter.sh ${KAFKA_DIR}
WORKDIR ${KAFKA_DIR}
CMD ./kafka-starter.sh