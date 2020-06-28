package dev.wornairz.tap.config;

import java.util.HashMap;
import java.util.Map;

import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.spark.SparkConf;

public class ConfigFactory {

	public static SparkConf getSparkConf() {
		SparkConf sparkConf = new SparkConf().setAppName("Ethereum Spark").setMaster("local[2]");
		sparkConf.set("es.index.auto.create", "true");
		sparkConf.set("es.nodes", "10.0.100.51");
		sparkConf.set("es.resource", "tap/eth");
		sparkConf.set("es.input.json", "yes");
	    //sparkConf.set("es.nodes.wan.only", "true");
		return sparkConf;
	}

	public static Map<String, Object> getKafkaStreamingConfig() {
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
