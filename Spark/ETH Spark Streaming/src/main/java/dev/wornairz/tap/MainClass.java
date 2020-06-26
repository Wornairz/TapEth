package dev.wornairz.tap;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
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
import org.apache.log4j.Logger;
import org.apache.spark.SparkConf;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.api.java.function.MapFunction;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.ml.regression.LinearRegression;
import org.apache.spark.ml.regression.LinearRegressionModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.catalyst.encoders.RowEncoder;

import static org.apache.spark.sql.functions.conv;
import static org.apache.spark.sql.functions.col;
import static org.apache.spark.sql.functions.lit;
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
import org.json.JSONArray;
import org.json.JSONObject;

import scala.Tuple2;

public class MainClass {

	private static SparkSession spark;
	private static LinearRegressionModel lrModel;
	private static Logger log = Logger.getLogger(MainClass.class);

	public static void main(String[] args) throws Exception {
		SparkConf sparkConf = new SparkConf().setAppName("Ethereum Spark").setMaster("local[2]");
		sparkConf.set("es.index.auto.create", "true");
		sparkConf.set("es.nodes", "10.0.100.51");
		sparkConf.set("es.resource", "tap/eth");
		sparkConf.set("es.input.json", "yes");
	    //sparkConf.set("es.nodes.wan.only", "true");
	    
		spark = SparkSession.builder().config(sparkConf).getOrCreate();
		spark.sparkContext().setLogLevel("WARN");
		
		JSONArray predictionJson = getPredictionJson();

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

		lrModel = lr.fit(training);
		printModelStats(lrModel);

		JavaStreamingContext streamingContext = new JavaStreamingContext(
				JavaSparkContext.fromSparkContext(spark.sparkContext()), Durations.seconds(1));

		Map<String, Object> kafkaParams = getKafkaStreamingConfig();
		Collection<String> topics = Arrays.asList("tap");

		JavaInputDStream<ConsumerRecord<String, String>> messageStream = KafkaUtils.createDirectStream(streamingContext,
				LocationStrategies.PreferConsistent(),
				ConsumerStrategies.<String, String>Subscribe(topics, kafkaParams));

		messageStream.mapToPair(record -> new Tuple2<>(record.key(), record.value())).map(tuple2 -> tuple2._2)
				.foreachRDD(MainClass::convertRDDtoDataset);

		streamingContext.start();
		streamingContext.awaitTermination();

		// spark.stop();
	}

	private static JSONArray getPredictionJson() throws IOException, InterruptedException, URISyntaxException {
		HttpClient client = HttpClient.newHttpClient();
		String prediction = client.send(HttpRequest.newBuilder(new URI(
				"https://ethgasstation.info/api/predictTable.json?api-key=0dd9fc3a3da130f1ad75664d09f4f849b7638819615c7d6cca229a7c943c"))
				.build(), BodyHandlers.ofString()).body();
		JSONArray predictionJson = new JSONArray(prediction);
		return predictionJson;
	}

	private static void convertRDDtoDataset(JavaRDD<String> rdd) {
		JavaRDD<Row> rowRDD = rdd.map(s -> RowFactory.create(s));
		Dataset<Row> dataset = spark.read().json(rdd);
		if (!dataset.isEmpty()) {
			dataset = dataset.drop("blockHash", "transactionIndex", "nonce", "input", "r", "s", "v", "blockNumber",
					"gas", "from", "to", "value", "hash");
			dataset = dataset.map((MapFunction<Row, Row>) MainClass::convertGasPriceToDouble, RowEncoder.apply(new StructType(new StructField[]{new StructField("gasPrice", DataTypes.DoubleType, false, Metadata.empty())})))
				.withColumn("gasPrice", conv(col("gasPrice"), 16, 10).cast(DataTypes.DoubleType));
			dataset = new VectorAssembler().setInputCols(new String[] { "gasPrice" }).setOutputCol("features")
					.transform(dataset).withColumn("label", lit(1d).cast(DataTypes.DoubleType));
			Dataset<Row> predictionDataset = lrModel.transform(dataset);
			predictionDataset.show();
			JavaEsSpark.saveJsonToEs(predictionDataset.toJSON().toJavaRDD(), "tap/eth");
		}
	}
	
	private static Row convertGasPriceToDouble(Row row) {
		String hexGasPrice = row.getString(0).substring(2);
		long gasPriceInWei = Long.parseLong(hexGasPrice, 16);
		double gasPriceInGwei = gasPriceInWei/1000000000;
		return RowFactory.create(gasPriceInGwei);
	}

	private static void printModelStats(LinearRegressionModel lrModel) {
		System.out.println("Variance: " + lrModel.summary().explainedVariance());
		System.out.println("Degrees of freedom: " + lrModel.summary().degreesOfFreedom());
		System.out.println("Mean Absolute Error: " + lrModel.summary().meanAbsoluteError());
		System.out.println("Mean Squared Error: " + lrModel.summary().meanSquaredError());
		System.out.println("Root Mean Squared Error: " + lrModel.summary().rootMeanSquaredError());
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
