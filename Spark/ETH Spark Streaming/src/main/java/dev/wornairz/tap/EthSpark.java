package dev.wornairz.tap;

import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.conv;
import static org.apache.spark.sql.functions.current_timestamp;
import static org.apache.spark.sql.functions.lit;

import java.io.Serializable;
import java.math.BigInteger;
import java.util.Arrays;
import java.util.Collection;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.ml.regression.LinearRegressionModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.catalyst.encoders.RowEncoder;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.Metadata;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka010.ConsumerStrategies;
import org.apache.spark.streaming.kafka010.KafkaUtils;
import org.apache.spark.streaming.kafka010.LocationStrategies;
import org.elasticsearch.spark.rdd.api.java.JavaEsSpark;

import dev.wornairz.tap.config.ConfigFactory;
import dev.wornairz.tap.ml.TrainingUtils;
import dev.wornairz.tap.spark.SparkWrapper;
import scala.Tuple2;

public class EthSpark implements Serializable {

	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;

	private SparkWrapper spark;
	private LinearRegressionModel lrModel;
	private transient JavaStreamingContext streamingContext;

	public EthSpark() throws InterruptedException {
		spark = SparkWrapper.getInstance();
		lrModel = TrainingUtils.getLinearRegressionPredictionModel();
		streamingContext = new JavaStreamingContext(JavaSparkContext.fromSparkContext(spark.getSparkContext()),
				Durations.seconds(1));
		startStreamProcessing();
	}

	private void startStreamProcessing() throws InterruptedException {
		getMessageStream().mapToPair(record -> new Tuple2<>(record.key(), record.value())).map(tuple2 -> tuple2._2)
				.foreachRDD(rdd -> predictEstimatedTimeThenSendToES(rdd));
		streamingContext.start();
		streamingContext.awaitTermination();
	}

	private JavaInputDStream<ConsumerRecord<String, String>> getMessageStream() {
		Map<String, Object> kafkaParams = ConfigFactory.getKafkaStreamingConfig();
		Collection<String> topics = Arrays.asList("tap");
		JavaInputDStream<ConsumerRecord<String, String>> messageStream = KafkaUtils.createDirectStream(streamingContext,
				LocationStrategies.PreferConsistent(),
				ConsumerStrategies.<String, String>Subscribe(topics, kafkaParams));
		return messageStream;
	}

	private void predictEstimatedTimeThenSendToES(JavaRDD<String> rdd) {
		Dataset<Row> dataset = spark.convertJsonRDDtoDataset(rdd);
		if (!dataset.isEmpty()) {
			dataset = dataset.drop("blockHash", "transactionIndex", "nonce", "input", "r", "s", "v", "blockNumber", "gas");
			dataset.show();
			dataset = dataset
					.map((MapFunction<Row, Row>) row -> convertHexValuesToDouble(row),
							RowEncoder.apply(new StructType(new StructField[] {
									new StructField("from", DataTypes.StringType, true, Metadata.empty()),
									new StructField("to", DataTypes.StringType, true, Metadata.empty()),
									new StructField("hash", DataTypes.StringType, false, Metadata.empty()),
									new StructField("value", DataTypes.DoubleType, true, Metadata.empty()),
									new StructField("gasPrice", DataTypes.DoubleType, false, Metadata.empty()),
									})));
			dataset = new VectorAssembler().setInputCols(new String[] { "gasPrice" }).setOutputCol("gas_price")
					.transform(dataset).drop("gasPrice");
			Dataset<Row> predictionDataset = lrModel.transform(dataset).withColumn("timestamp",
					lit(current_timestamp().cast(DataTypes.TimestampType)));
			predictionDataset.show(100, false);
			JavaEsSpark.saveJsonToEs(predictionDataset.toJSON().toJavaRDD(), "tap/eth");
		}
	}

	private Row convertHexValuesToDouble(Row row) {
		String from = row.getString(0);
		String hexGasPrice = row.getString(1).substring(2);
		String hash = row.getString(2);
		String to = row.getString(3);
		String hexValue = row.getString(4).substring(2);
		long gasPriceInWei = Long.parseLong(hexGasPrice, 16);
		double gasPriceInGwei = gasPriceInWei / 1000000000;
		BigInteger[] valueInEth = new BigInteger(hexValue, 16).divideAndRemainder(new BigInteger("1000000000000000000"));
		double valueInEthDouble = Double.parseDouble(valueInEth[0].toString() + "." + valueInEth[1].toString());
		return RowFactory.create(from, to, hash, valueInEthDouble, gasPriceInGwei);
	}
}
