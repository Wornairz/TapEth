package dev.wornairz.tap.ml;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse.BodyHandlers;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

import org.apache.log4j.Logger;
import org.apache.spark.ml.feature.VectorAssembler;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.RowFactory;
import org.apache.spark.sql.types.DataTypes;
import org.apache.spark.sql.types.StructField;
import org.apache.spark.sql.types.StructType;
import org.json.JSONArray;
import org.json.JSONObject;

import dev.wornairz.tap.MainClass;
import dev.wornairz.tap.spark.SparkWrapper;

public class PredictionUtils {
	
	private static String apiKey;
	private static Logger log = Logger.getLogger(MainClass.class);

	static {
		try {
			apiKey = Files.readString(Paths.get("ethgasstation-apikey.txt"));
		} catch (IOException e) {
			log.error("Cannot read api key");
			System.exit(5);
		}
	}
	
	public static Dataset<Row> getPredictionDataset() {
		JSONArray predictionJson = getPredictionJson();
		Dataset<Row> training = createPredictionDataset(predictionJson);
		training = new VectorAssembler().setInputCols(new String[] { "gasprice" }).setOutputCol("gas_price")
				.transform(training).drop("gasprice");
		training.show(100, false);
		return training;
	}
	
	private static JSONArray getPredictionJson() {
		HttpClient client = HttpClient.newHttpClient();
		String prediction = "[]";
		try {
			prediction = client.send(HttpRequest.newBuilder(new URI(
					"https://ethgasstation.info/api/predictTable.json?api-key=" + apiKey))
					.build(), BodyHandlers.ofString()).body();
		} catch (IOException | InterruptedException | URISyntaxException e) {
			e.printStackTrace();
		}
		JSONArray predictionJson = new JSONArray(prediction);
		return predictionJson;
	}

	private static Dataset<Row> createPredictionDataset(JSONArray predictionJson) {
		List<Row> data = new ArrayList<>();
		for (int i = 0; i < predictionJson.length(); i++) {
			JSONObject object = predictionJson.getJSONObject(i);
			data.add(RowFactory.create(object.getDouble("gasprice"), object.getDouble("expectedTime")));
		}
		Dataset<Row> training = SparkWrapper.getInstance().createDataset(data, getPredictionSchema());
		return training;
	}

	private static StructType getPredictionSchema() {
		StructType schema = DataTypes.createStructType(
				new StructField[] { DataTypes.createStructField("gasprice", DataTypes.DoubleType, false),
						DataTypes.createStructField("expectedTime", DataTypes.DoubleType, false), });
		return schema;
	}
}
