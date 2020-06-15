package ethereum;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

public class EthereumBlocksQueue {

	private Queue<String> queue;
	private static EthereumBlocksQueue instance;
	
	private EthereumBlocksQueue() {
		queue = new ConcurrentLinkedQueue<>();
	}
	
	public static EthereumBlocksQueue getInstance() {
		if(instance == null)
			instance = new EthereumBlocksQueue();
		return instance;
	}
	
	public void add(String s) {
		queue.add(s);
	}
	
	public String peek() {
		return queue.peek();
	}
	
	public boolean isEmpty() {
		return queue.isEmpty();
	}
}
