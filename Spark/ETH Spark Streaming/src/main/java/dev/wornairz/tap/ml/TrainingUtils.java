package dev.wornairz.tap.ml;

import org.apache.spark.ml.regression.LinearRegression;
import org.apache.spark.ml.regression.LinearRegressionModel;
import org.apache.spark.ml.regression.LinearRegressionSummary;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

public class TrainingUtils {

	public static LinearRegressionModel getLinearRegressionPredictionModel() {
		LinearRegressionModel lrModel;
		Dataset<Row> training = TrainingModel.getTrainingDataset();
		LinearRegression lr = new LinearRegression().setMaxIter(1000).setRegParam(0.3).setElasticNetParam(0.8)
				.setFeaturesCol("gas_price").setLabelCol("expectedTime").setPredictionCol("expected_time");
		lrModel = lr.fit(training);
		printModelStats(lrModel.evaluate(training));
		return lrModel;
	}

	private static void printModelStats(LinearRegressionSummary linearRegressionSummary) {
		System.out.println("Variance: " + linearRegressionSummary.explainedVariance());
		System.out.println("Degrees of freedom: " + linearRegressionSummary.degreesOfFreedom());
		System.out.println("Mean Absolute Error: " + linearRegressionSummary.meanAbsoluteError());
		System.out.println("Mean Squared Error: " + linearRegressionSummary.meanSquaredError());
		System.out.println("Root Mean Squared Error: " + linearRegressionSummary.rootMeanSquaredError());
	}
}
