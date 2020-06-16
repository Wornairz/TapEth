package ethereum;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class EthereumBlocksQueue {

	private BlockingQueue<String> queue;
	private static EthereumBlocksQueue instance;
	
	private EthereumBlocksQueue() {
		queue = new LinkedBlockingQueue<>();
	}
	
	public static EthereumBlocksQueue getInstance() {
		if(instance == null)
			instance = new EthereumBlocksQueue();
		return instance;
	}
	
	public void add(String s) {
		try {
			queue.put(s);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}
	
	public String remove() {
		try {
			return queue.take();
		} catch (InterruptedException e) {
			e.printStackTrace();
			return null;
		}
	}
	
	public boolean isEmpty() {
		return queue.isEmpty();
	}
}
