import ethereum.EthereumBlocksQueue;
import ethereum.EthereumWSSClient;

public class MainClass {

	public static void main(String[] args) {
		if(args[0].startsWith("wss://")) {
			String webSocketUrl = args[0];
			new EthereumWSSClient(webSocketUrl).start();
			EthereumBlocksQueue queue = EthereumBlocksQueue.getInstance();
			while (true) {
				String s = queue.remove();
				System.out.println("CODA S : " + s);
			}
			
		}
		else
			System.err.println("Provided url is not a websocket url");
	}
}
