package dev.wornairz.tap;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.spark.SparkConf;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.ml.regression.LinearRegression;
import org.apache.spark.ml.regression.LinearRegressionModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka010.ConsumerStrategies;
import org.apache.spark.streaming.kafka010.KafkaUtils;
import org.apache.spark.streaming.kafka010.LocationStrategies;

import org.json.JSONArray;
import org.json.JSONObject;

import scala.Tuple2;

public class MainClass {

	public static void main(String[] args) throws Exception {
		SparkConf sparkConf = new SparkConf().setAppName("Ethereum Spark").setMaster("local[2]");
		sparkConf.set("es.index.auto.create", "true");

		SparkSession spark = SparkSession.builder().config(sparkConf).getOrCreate();

		HttpClient client = HttpClient.newHttpClient();
		String prediction = client.send(HttpRequest.newBuilder(new URI(
				"https://ethgasstation.info/api/predictTable.json?api-key=0dd9fc3a3da130f1ad75664d09f4f849b7638819615c7d6cca229a7c943c"))
				.build(), BodyHandlers.ofString()).body();
		JSONArray predictionJson = new JSONArray(prediction);

		List<Row> data = new ArrayList<>();
		for (int i = 0; i < predictionJson.length(); i++) {
			JSONObject object = predictionJson.getJSONObject(i);
			data.add(RowFactory.create(object.getDouble("gasprice"), object.getDouble("expectedTime")));
		}
		Dataset<Row> training = spark.createDataFrame(data, getSchema());
		training = new VectorAssembler().setInputCols(new String[] { "gasprice" }).setOutputCol("features")
				.transform(training).withColumnRenamed("expectedTime", "label");
		training.show();

		LinearRegression lr = new LinearRegression().setMaxIter(10).setRegParam(0.3).setElasticNetParam(0.8);

		LinearRegressionModel lrModel = lr.fit(training);
		printModelStats(lrModel);

		JavaStreamingContext streamingContext = new JavaStreamingContext(sparkConf, Durations.seconds(1));

		Map<String, Object> kafkaParams = getKafkaStreamingConfig();
		Collection<String> topics = Arrays.asList("tap");

		JavaInputDStream<ConsumerRecord<String, String>> messageStream = KafkaUtils.createDirectStream(streamingContext,
				LocationStrategies.PreferConsistent(),
				ConsumerStrategies.<String, String>Subscribe(topics, kafkaParams));

		messageStream.mapToPair(record -> new Tuple2<>(record.key(), record.value())).map(tuple2 -> tuple2._2)
				.foreachRDD(rdd -> {
					List<String> blocks = rdd.collect();
					if (!blocks.isEmpty())
						System.out.println(blocks);
				});

		streamingContext.start();
		streamingContext.awaitTermination();

		//spark.stop();
	}

	private static void printModelStats(LinearRegressionModel lrModel) {
		System.out.println("Variance: " + lrModel.summary().explainedVariance());
		System.out.println("Degrees of freedom: " + lrModel.summary().degreesOfFreedom());
		System.out.println("Mean Absolute Error: " + lrModel.summary().meanAbsoluteError());
		System.out.println("Mean Squared Error: " + lrModel.summary().meanSquaredError());
		System.out.println("Root Mean Squared Error: " + lrModel.summary().rootMeanSquaredError());
		// System.out.println("Coefficient standard errors: " +
		// lrModel.summary().coefficientStandardErrors());
		// System.out.println("Deviance residuals: " +
		// lrModel.summary().devianceResiduals());
		// System.out.println("Objective history: " +
		// lrModel.summary().objectiveHistory());
		// System.out.println("P-Values: " + lrModel.summary().pValues());
		// System.out.println("T-Values: " + List.of(lrModel.summary().tValues()));
	}

	private static StructType getSchema() {
		StructType schema = DataTypes.createStructType(
				new StructField[] { DataTypes.createStructField("gasprice", DataTypes.DoubleType, false),
						DataTypes.createStructField("expectedTime", DataTypes.DoubleType, false), });
		return schema;
	}

	private static Map<String, Object> getKafkaStreamingConfig() {
		Map<String, Object> kafkaParams = new HashMap<>();
		kafkaParams.put("bootstrap.servers", "10.0.100.25:9092");
		kafkaParams.put("key.deserializer", StringDeserializer.class);
		kafkaParams.put("value.deserializer", StringDeserializer.class);
		kafkaParams.put("group.id", "connect-cluster");
		kafkaParams.put("auto.offset.reset", "latest");
		kafkaParams.put("enable.auto.commit", false);
		return kafkaParams;
	}

}
