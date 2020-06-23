FROM maven:latest

ENV SPARK_VERSION=3.0.0
ENV SPARK_DIR=/opt/spark
ENV PATH $SPARK_DIR/bin:$PATH
ENV ETH_SPARK_STREAMING "ETH Spark Streaming"
ENV ETH_SPARK_DIR "/opt/spark-eth/"

ADD spark-${SPARK_VERSION}-bin-hadoop2.7.tgz /opt
RUN ln -s /opt/spark-${SPARK_VERSION}-bin-hadoop2.7 ${SPARK_DIR} 

COPY ${ETH_SPARK_STREAMING} ${ETH_SPARK_DIR}
WORKDIR ${ETH_SPARK_DIR}
RUN mvn package
RUN cp target/eth-spark-streaming-* ${ETH_SPARK_DIR}


COPY spark-starter.sh ${ETH_SPARK_DIR}
WORKDIR ${ETH_SPARK_DIR}
CMD ./spark-starter.sh