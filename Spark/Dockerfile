FROM openjdk:8

ENV SPARK_VERSION=3.0.0
ENV SPARK_DIR=/opt/spark
ENV PATH $SPARK_DIR/bin:$PATH

ADD spark-${SPARK_VERSION}-bin-hadoop2.7.tgz /opt

RUN apt-get update

# Create Sym Link 
RUN ln -s /opt/spark-${SPARK_VERSION}-bin-hadoop2.7 ${SPARK_DIR} 

# Add Java Code
COPY eth-spark-streaming-0.0.1-SNAPSHOT.jar /opt/spark-eth/
# Add Spark Manager
COPY spark-starter.sh /opt/spark-eth/

WORKDIR /opt/spark-eth
CMD ./spark-starter.sh