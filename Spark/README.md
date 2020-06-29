# Spark for TapEth

## What is Apache Spark

[Apache Spark](https://spark.apache.org/) is a unified analytics engine for big data and machine learning.
It's fault-tolerant.

## Configuration

Into the ETH Spark Streaming folder, replace the _ethgasstation-apikey.txt.dist_ into _ethgasstation-apikey.txt_, then insert your [ETH Gas Station](https://docs.ethgasstation.info/) API key.

## Maven dependecies

```xml
<dependencies>
	<dependency>
		<groupId>org.apache.spark</groupId>
		<artifactId>spark-core_2.12</artifactId>
		<version>3.0.0</version>
		<scope>provided</scope>
	</dependency>
	<dependency>
		<groupId>org.apache.spark</groupId>
		<artifactId>spark-streaming_2.12</artifactId>
		<version>3.0.0</version>
		<scope>provided</scope>
	</dependency>
	<dependency>
		<groupId>org.apache.spark</groupId>
		<artifactId>spark-sql_2.12</artifactId>
		<version>3.0.0</version>
		<scope>provided</scope>
	</dependency>
	<dependency>
		<groupId>org.apache.spark</groupId>
		<artifactId>spark-streaming-kafka-0-10_2.12</artifactId>
		<version>3.0.0</version>
	</dependency>
	<dependency>
		<groupId>org.apache.spark</groupId>
		<artifactId>spark-mllib_2.12</artifactId>
		<version>3.0.0</version>
		<scope>provided</scope>
	</dependency>

	<dependency>
		<groupId>org.json</groupId>
		<artifactId>json</artifactId>
		<version>20200518</version>
	</dependency>
	<dependency>
		<groupId>org.elasticsearch</groupId>
		<artifactId>elasticsearch-hadoop</artifactId>
		<version>7.8.0</version>
	</dependency>
</dependencies>
```

The ```<scope>provided</scope>``` is necessary to tell the Maven compiler to not include those dependencies into the jar as they will be provided by the Spark framework.

- Spark Core provides the main Spark API
- Spark Streaming provides the [Spark Streaming](https://spark.apache.org/docs/latest/streaming-programming-guide.html) extension for Spark
- Spark SQL is used for data types
- [Spark Streaming Kafka](https://spark.apache.org/docs/latest/streaming-kafka-0-10-integration.html) 0.10 as the name says, is used for communication between Spark and Kafka
- [Spark MLlib](https://spark.apache.org/mllib/) is the Spark machine learning library
  
- [org.json](https://github.com/stleary/JSON-java) is used for JSON parsing
- ElasticSearch-Hadoop is used for easily write into ElasticSearch from Spark

## How TapEth Spark works

In brief:
1. Start the Spark Session
2. Get the training data from [ETH Gas Station](https://docs.ethgasstation.info/#prediction-table)
3. Train the LinearRegressionModel with the ETH Gas Station prediction table
4. Get the pending transaction data from Kafka Topic using Spark Streaming Kafka
5. Process the pending transaction data using the LinearRegressionModel
6. Send the output data to ElasticSearch

### Training

The training uses the [ETH Gas Station prediction table](https://docs.ethgasstation.info/#prediction-table) as model. ETH Gas Station updates regularly this prediction by checking the data of the last 200 transactions. <br>
After getting the Eth Gas Station's prediction table, we can create our LinearRegressionModel:
```Java
LinearRegressionModel lrModel;
Dataset<Row> training = TrainingModel.getTrainingDataset();
LinearRegression lr = new LinearRegression().setMaxIter(100).setRegParam(0.3).setElasticNetParam(0.8)
		.setFeaturesCol("gas_price").setLabelCol("expectedTime").setPredictionCol("expected_time");
lrModel = lr.fit(training);
return lrModel;
```

### Streaming

We configure the Streaming to read (subscribe) from the "tap" Kafka Topic. Then we get only the incoming data (pending transaction), filtering out the unecessary information (key).

```Java
private void startStreamProcessing() throws InterruptedException {
	getMessageStream().mapToPair(record -> new Tuple2<>(record.key(), record.value()))
        .map(tuple2 -> tuple2._2)
		.foreachRDD(rdd -> predictEstimatedTimeThenSendToES(rdd));
	streamingContext.start();
	streamingContext.awaitTermination();
}

private JavaInputDStream<ConsumerRecord<String, String>> getMessageStream() {
	Map<String, Object> kafkaParams = ConfigFactory.getKafkaStreamingConfig();
	Collection<String> topics = Arrays.asList("tap");
    JavaInputDStream<ConsumerRecord<String, String>> messageStream = KafkaUtils.createDirectStream(
        streamingContext, LocationStrategies.PreferConsistent(),
        ConsumerStrategies.<String, String>Subscribe(topics, kafkaParams));
	return messageStream;
}
```

### Processing

Here, we further remove any unecessary information and convert the hex gas price into a double value (representing GWEI units).<br>
Then, the filtered input dataset is evaluated by the LinearRegressionModel that returns the same dataset with the prediction column indicating the estimated waiting time for that transaction.<br>
The data is finally sent to ElasticSearch, ready for being indexed.

```Java
private void predictEstimatedTimeThenSendToES(JavaRDD<String> rdd) {
	Dataset<Row> dataset = spark.convertJsonRDDtoDataset(rdd);
	if (!dataset.isEmpty()) {
		dataset = dataset.drop("blockHash", "transactionIndex", "nonce", "input", "r", "s", "v", "blockNumber");
		dataset = dataset
				.map((MapFunction<Row, Row>) row -> convertGasPriceToDouble(row),
						RowEncoder.apply(new StructType(new StructField[] {
								new StructField("gasPrice", DataTypes.DoubleType, false, Metadata.empty()) })))
				.withColumn("gasPrice", conv(col("gasPrice"), 16, 10).cast(DataTypes.DoubleType));
		dataset = new VectorAssembler().setInputCols(new String[] { "gasPrice" }).setOutputCol("gas_price")
				.transform(dataset).drop("gasPrice");
		Dataset<Row> predictionDataset = lrModel.transform(dataset).withColumn("timestamp",
				lit(current_timestamp().cast(DataTypes.TimestampType)));
		predictionDataset.show(100, false);
		JavaEsSpark.saveJsonToEs(predictionDataset.toJSON().toJavaRDD(), "tap/eth");
	}
}
```

## Run TapEth Spark

You can start the application using **Docker**, building and running the Dockerfile provided in this repo. <br>
The script _spark-starter.sh_, used as ENTRYPOINT of the Dockerfile, simply submits the created fat jar and sets the application's entrypoint.