import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;

public class MainClass {

	public static void main(String[] args) {
		if(args[0].startsWith("wss://")) {
			String webSocketUrl = args[0];
			OkHttpClient client = new OkHttpClient();
	    	Request request = new Request.Builder().url(webSocketUrl).build();
			WebSocket webSocket = client.newWebSocket(request, new EthereumWSSListener());
		}
		else
			System.err.println("Provided url is not a websocket url");
	}
}
