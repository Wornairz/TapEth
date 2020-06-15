package ethereum;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class EthereumWSSListener extends WebSocketListener {
	
	private EthereumBlocksQueue queue;
	
	@Override
	public void onOpen(WebSocket webSocket, Response response) {
		System.out.println("Connected successfully");
		queue = EthereumBlocksQueue.getInstance();
		webSocket.send("{\"jsonrpc\":\"2.0\", \"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"newHeads\"]}");
	}
	
	@Override
	public void onMessage(WebSocket webSocket, String text) {
		System.out.println(text);
		queue.add(text);
	}
	
	@Override
	public void onClosed(WebSocket webSocket, int code, String reason) {
		System.out.println("Connection closed");
	}
	
	@Override
	public void onFailure(WebSocket webSocket, Throwable t, Response response) {
		System.err.println(response);
		t.printStackTrace();
	}
}
