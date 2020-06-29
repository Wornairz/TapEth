# Kafka for TapEth

## What is Apache Kafka

[Apache Kafka](https://kafka.apache.org/) is an open-source stream-processing software platform. Kafka provide a unified, high-throughput, low-latency platform for handling real-time data feeds. Kafka can connect to external systems (for data import/export) via [Kafka Connect](https://kafka.apache.org/documentation.html#connect). Kafka it's also horizontally scalable and fault-tolerant.

### Maven dependencies

```xml
<dependencies>
	<dependency>
		<groupId>org.apache.kafka</groupId>
		<artifactId>connect-api</artifactId>
		<version>2.4.1</version>
	</dependency>
	<dependency>
		<groupId>com.squareup.okhttp3</groupId>
		<artifactId>okhttp</artifactId>
		<version>4.7.2</version>
	</dependency>
	<dependency>
		<groupId>org.json</groupId>
		<artifactId>json</artifactId>
		<version>20200518</version>
	</dependency>
</dependencies>
```
- **connect-api** is the main Kafka Connect API. Available only in Java or Scala, they are used for building the custom Kafka Connector.
- [OkHttp3](https://square.github.io/okhttp/) is used for websockets
- [org.json](https://github.com/stleary/JSON-java) is used for parsing the JSON incoming from the Ethereum JSON-RPC API.

## Configuration

Rename the file _eth-source-connector.properties.dist_ into _eth-source-connector.properties_ and insert your Infura WSS URI.

## How TapEth gets the raw data

The data is taken from [Infura](https://infura.io/), a services that provides a [geth](https://geth.ethereum.org/) node. TapEth subscribes to pub/sub "eth_subscribe" method call of the JSON-RPC API, immediately after connecting to the Infura websocket url.<br>
[Some calls to the Infura API could result in a null transaction](https://community.infura.io/t/web3-eth-gettransaction-txhash-returns-null/814/4): this bad data is filtered out and not written into the Kafka topic.<br>

```Java
@Override
public void onOpen(WebSocket webSocket, Response response) {
	queue = EthereumBlocksQueue.getInstance();
	webSocket.send(subscribeJson.toString());
}

@Override
public void onMessage(WebSocket webSocket, String text) {
	JSONObject response = new JSONObject(text);
	if(response.has("error")) {
		System.exit(response.getJSONObject("error").getInt("code"));
	}
	else if (response.has("params"))
		sendGetTransactionByHashRequest(webSocket, response);
	else if (response.has("result"))
		addResponseToQueue(response);
}
```

You can check the <a href="./ETH Kafka Connector/src/main/java/dev/wornairz/tap/ethereum/">ethereum package</a> of this project's Kafka Connector for more information.

## How TapEth streams the data

In order to create your own Source Kafka Connector, you must create one class that extends **SourceConnector** and one other that extends **SourceTask**.<br>
The SourceConnector class is the entrypoint of the Kafka Connector and is responsible of configuring and creating the SourceTask(s), that are instead responsible of getting the effective work done and writing the data into the Kafka topic. <br>
```Java
public class EthereumSourceConnector extends SourceConnector {
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
}
```
Simply the **poll()** method of the SourceTask is called periodically (default every 3s) by Kafka Connect and here we must return our data. A BlockingQueue is used for easiness.<br>
```Java
public class EthereumSourceTask extends SourceTask {
	@Override
	public void start(Map<String, String> props) {
		//The configuration map is passed by the SourceConnector
		kafkaTopic = props.get("topic");
		queue = EthereumBlocksQueue.getInstance();
		count = 0L;
	}

	@Override
	public List<SourceRecord> poll() {
		List<SourceRecord> records = new ArrayList<>();
		while (!queue.isEmpty()) {
			String block = queue.remove();
			SourceRecord record = new SourceRecord(offsetKey(OFFSET_KEY), offsetValue(count++),
										kafkaTopic, Schema.STRING_SCHEMA, block);
			records.add(record);
		}
		return records;
	}
}
```
You can check the <a href="./ETH Kafka Connector/src/main/java/dev/wornairz/tap/kafka/">kafka package</a> of this project's Kafka Connector for more information.

### Creating the Fat Jar

Kafka Connect requires a fat/uber jar, that simply is a jar with all dependencies in it. <br>
You can easily get a fat jar from your _Maven_ project by using the **maven shade plugin**:

```xml
<plugin>
	<groupId>org.apache.maven.plugins</groupId>
	<artifactId>maven-shade-plugin</artifactId>
	<version>3.2.4</version>
	<configuration>
	</configuration>
	<executions>
		<execution>
			<phase>package</phase>
			<goals>
				<goal>shade</goal>
			</goals>
		</execution>
    </executions>
</plugin>
```

## Run TapEth Kafka

You can start the connector using **Docker**, building and running the Dockerfile provided in this repo. <br>
The script _kafka-starter.sh_, used as ENTRYPOINT of the Dockerfile, simply starts the Kafka Server then, using the connect api (throught the Kafka's shell script), deploys the connector into the running Kafka Server in **standalone mode**.