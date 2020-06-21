package dev.wornairz.tap.kafka;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.kafka.common.config.ConfigDef;
import org.apache.kafka.common.config.ConfigDef.Importance;
import org.apache.kafka.common.config.ConfigDef.Type;
import org.apache.kafka.connect.connector.Task;
import org.apache.kafka.connect.source.SourceConnector;

import dev.wornairz.tap.ethereum.EthereumWSSClient;

public class EthereumSourceConnector extends SourceConnector {

	private String ethereumWssUri;
	private String kafkaTopic;
	private EthereumWSSClient ethereumWssClient;

	@Override
	public String version() {
		return "1";
	}

	@Override
	public void start(Map<String, String> props) {
		//Read configuration from .properties file
		ethereumWssUri = props.get("wss");
		kafkaTopic = props.get("topic");
		ethereumWssClient = new EthereumWSSClient(ethereumWssUri);
		ethereumWssClient.start();
	}

	@Override
	public Class<? extends Task> taskClass() {
		return EthereumSourceTask.class;
	}

	@Override
	public List<Map<String, String>> taskConfigs(int maxTasks) {
		//Write configuration to pass to each task
		List<Map<String, String>> configs = new ArrayList<>();
		Map<String, String> config = new HashMap<>();
		config.put("topic", kafkaTopic);
		configs.add(config);
		return configs;
	}

	@Override
	public void stop() {
		ethereumWssClient.stop();
	}

	@Override
	public ConfigDef config() {
		return new ConfigDef()
				.define("wss", Type.STRING, Importance.HIGH, "The Ethereum WSS URL")
				.define("topic", Type.STRING, Importance.HIGH, "The Kafka topic");
	}

}
