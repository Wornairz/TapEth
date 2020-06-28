package dev.wornairz.tap.ml;

import org.apache.spark.ml.regression.LinearRegression;
import org.apache.spark.ml.regression.LinearRegressionModel;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;

public class TrainingUtils {

	public static LinearRegressionModel getLinearRegressionPredictionModel() {
		LinearRegressionModel lrModel;
		Dataset<Row> training = PredictionUtils.getPredictionDataset();
		LinearRegression lr = new LinearRegression().setMaxIter(100).setRegParam(0.3).setElasticNetParam(0.8)
				.setFeaturesCol("gas_price").setLabelCol("expectedTime").setPredictionCol("expected_time");
		lrModel = lr.fit(training);
		printModelStats(lrModel);
		return lrModel;
	}

	private static void printModelStats(LinearRegressionModel lrModel) {
		System.out.println("Variance: " + lrModel.summary().explainedVariance());
		System.out.println("Degrees of freedom: " + lrModel.summary().degreesOfFreedom());
		System.out.println("Mean Absolute Error: " + lrModel.summary().meanAbsoluteError());
		System.out.println("Mean Squared Error: " + lrModel.summary().meanSquaredError());
		System.out.println("Root Mean Squared Error: " + lrModel.summary().rootMeanSquaredError());
	}
}
