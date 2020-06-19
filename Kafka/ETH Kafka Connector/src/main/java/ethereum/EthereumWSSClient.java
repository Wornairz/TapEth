package ethereum;

import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.WebSocket;

public class EthereumWSSClient {
	
	private OkHttpClient httpClient;
	private WebSocket webSocket;
	private String wssUrl;
	
	public EthereumWSSClient(String url) {
		httpClient = new OkHttpClient();
		wssUrl = url;
	}
	
	public void start() {
    	Request request = new Request.Builder().url(wssUrl).build();
		webSocket = httpClient.newWebSocket(request, new EthereumWSSListener());
	}
	
	public void stop() {
		webSocket.close(0, "Kafka Stop");
	}
}
