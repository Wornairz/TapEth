package kafka;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.kafka.connect.data.Schema;
import org.apache.kafka.connect.source.SourceRecord;
import org.apache.kafka.connect.source.SourceTask;

import ethereum.EthereumBlocksQueue;

public class EthereumSourceTask extends SourceTask {

	private String ethereumWssUri;
	private String kafkaTopic;
	private EthereumBlocksQueue queue;
	private Long count;

	@Override
	public String version() {
		return "1";
	}

	@Override
	public void start(Map<String, String> props) {
		ethereumWssUri = props.get("wss");
		kafkaTopic = props.get("topic");
		queue = EthereumBlocksQueue.getInstance();
		count = 0L;
	}

	@Override
	public List<SourceRecord> poll() throws InterruptedException {
		List<SourceRecord> records = new ArrayList<>();
		while (records.isEmpty() && !queue.isEmpty()) {
			String block = queue.remove();
			SourceRecord record = new SourceRecord(offsetKey(ethereumWssUri), offsetValue(count++), kafkaTopic,
					Schema.STRING_SCHEMA, block);
			records.add(record);
		}
		return records;
	}

	@Override
	public synchronized void stop() {
		// TODO Auto-generated method stub
	}

	private Map<String, String> offsetKey(String wss) {
		return Collections.singletonMap("wss", wss);
	}

	private Map<String, Long> offsetValue(Long pos) {
		return Collections.singletonMap("position", pos);
	}
}
