package dev.wornairz.tap.ethereum;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;

public class EthereumWSSListener extends WebSocketListener {

	private EthereumBlocksQueue queue;
	public static final JSONObject subscribeJson = new JSONObject(
			"{\"jsonrpc\":\"2.0\", \"id\": 1, \"method\": \"eth_subscribe\", \"params\": [\"newPendingTransactions\"]}");
	public static final JSONObject getTransactionByHashJson = new JSONObject(
			"{\"jsonrpc\":\"2.0\",\"method\":\"eth_getTransactionByHash\",\"params\": [],\"id\":1}");

	@Override
	public void onOpen(WebSocket webSocket, Response response) {
		System.out.println("Connected successfully");
		queue = EthereumBlocksQueue.getInstance();
		webSocket.send(subscribeJson.toString());
	}

	@Override
	public void onMessage(WebSocket webSocket, String text) {
		JSONObject response = new JSONObject(text);
		if (response.has("params")) {
			String transactionHash = response.getJSONObject("params").getString("result");
			JSONObject getTransactionByHashRequest = new JSONObject(getTransactionByHashJson.toString());
			JSONArray paramsArray = new JSONArray();
			paramsArray.put(transactionHash);
			getTransactionByHashRequest.put("params", paramsArray);
			CompletableFuture.runAsync(() -> {
				waitFor(5, TimeUnit.SECONDS);
				webSocket.send(getTransactionByHashRequest.toString());
			});
		} else if (response.has("result")) {
			try {
				queue.add(response.getJSONObject("result").toString());
			} catch (JSONException e) {
				System.err.println(response);
			}
		}
	}

	private static void waitFor(long time, TimeUnit timeUnit) {
		try {
			timeUnit.sleep(time);
		} catch (InterruptedException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void onClosed(WebSocket webSocket, int code, String reason) {
		System.out.println("Connection closed");
	}

	@Override
	public void onFailure(WebSocket webSocket, Throwable t, Response response) {
		System.err.println("FAILURE! :" + response);
		t.printStackTrace();
	}
}
