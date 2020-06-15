FROM openjdk:8
ENV PATH /opt/kafka/bin:$PATH
ENV KAFKA_DIR "/opt/kafka"

ARG KAFKA_VERSION="2.12-2.5.0"

RUN apt-get update && apt-get install telnet && apt-get install nano 

# Installing Kafka
# ADD will automatically extract the file
ADD kafka_${KAFKA_VERSION}.tgz /opt

# Create Sym Link 
RUN ln -s /opt/kafka_${KAFKA_VERSION} ${KAFKA_DIR} 

# Copy All conf here
COPY eth-source-connector.properties ${KAFKA_DIR}/conf/
COPY worker.properties /opt/kafka/conf/

COPY eth-connector-kafka-* /usr/local/share/kafka/plugins/
