package dev.wornairz.tap.spark;

import java.io.Serializable;
import java.util.List;

import org.apache.spark.SparkConf;
import org.apache.spark.SparkContext;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.sql.Dataset;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SparkSession;
import org.apache.spark.sql.types.StructType;

import dev.wornairz.tap.config.ConfigFactory;

public class SparkWrapper implements Serializable{
	
	/**
	 * 
	 */
	private static final long serialVersionUID = 1L;
	
	private static SparkSession spark;
	private static SparkWrapper instance;
	
	static {
		SparkConf sparkConf = ConfigFactory.getSparkConf();
		spark = SparkSession.builder().config(sparkConf).getOrCreate();
		spark.sparkContext().setLogLevel("ERROR");
		instance = new SparkWrapper();
	}
	
	public static SparkWrapper getInstance() {
		return instance;
	}
	
	public SparkSession getSparkSession() {
		return spark;
	}
	
	public SparkContext getSparkContext() {
		return spark.sparkContext();
	}
	
	public Dataset<Row> createDataset(List<Row> data, StructType schema) {
		return spark.createDataFrame(data, schema);
	}
	
	public Dataset<Row> convertJsonRDDtoDataset(JavaRDD<String> rdd){
		Dataset<Row> dataset = spark.read().json(rdd);
		return dataset;
	}
}
