import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.spark.SparkConf;
import org.apache.spark.streaming.Durations;
import org.apache.spark.streaming.api.java.JavaInputDStream;
import org.apache.spark.streaming.api.java.JavaStreamingContext;
import org.apache.spark.streaming.kafka010.ConsumerStrategies;
import org.apache.spark.streaming.kafka010.KafkaUtils;
import org.apache.spark.streaming.kafka010.LocationStrategies;

import scala.Tuple2;

public class MainClass {

	public static void main(String[] args) throws InterruptedException {
		SparkConf sparkConf = new SparkConf();
		sparkConf.setAppName("Ethereum Spark");
		 
		JavaStreamingContext streamingContext = new JavaStreamingContext(
		  sparkConf, Durations.seconds(1));
		
		Map<String, Object> kafkaParams = new HashMap<>();
		kafkaParams.put("bootstrap.servers", "10.0.100.25:9092");
		kafkaParams.put("key.deserializer", StringDeserializer.class);
		kafkaParams.put("value.deserializer", StringDeserializer.class);
		kafkaParams.put("group.id", "connect-cluster");
		kafkaParams.put("auto.offset.reset", "latest");
		kafkaParams.put("enable.auto.commit", false);
		Collection<String> topics = Arrays.asList("tap");
		
		JavaInputDStream<ConsumerRecord<String, String>> messageStream = 
				  KafkaUtils.createDirectStream(
				    streamingContext, 
				    LocationStrategies.PreferConsistent(), 
				    ConsumerStrategies.<String, String> Subscribe(topics, kafkaParams));
				
		messageStream.mapToPair(record -> new Tuple2<>(record.key(), record.value()))
			.map(tuple2 -> tuple2._2)
			.foreachRDD(rdd -> {
				List<String> blocks = rdd.collect();
				System.out.println(blocks);
			});
		
		streamingContext.start();
		streamingContext.awaitTermination();
	}

}
