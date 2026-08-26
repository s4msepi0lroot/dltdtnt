package ru.sepiolsmp.skins.net;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * Tiny HTTP helper built on the JDK client. No external libraries.
 * Never call any of this from the main server thread.
 */
public final class Http {

	public static final String USER_AGENT = "SepiolSkins/0.1.0 (sepiolSMP)";

	private final HttpClient client;

	public Http() {
		this.client = HttpClient.newBuilder()
				.connectTimeout(Duration.ofSeconds(10))
				.followRedirects(HttpClient.Redirect.NORMAL)
				.build();
	}

	public Response get(String url, int timeoutSeconds) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(Math.max(2, timeoutSeconds)))
				.header("User-Agent", USER_AGENT)
				.header("Accept", "application/json")
				.GET()
				.build();
		HttpResponse<String> response =
				client.send(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		return new Response(response.statusCode(), response.body());
	}

	public byte[] bytes(String url, int timeoutSeconds) throws IOException, InterruptedException {
		HttpRequest request = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(Math.max(2, timeoutSeconds)))
				.header("User-Agent", USER_AGENT)
				.GET()
				.build();
		HttpResponse<byte[]> response = client.send(request, HttpResponse.BodyHandlers.ofByteArray());
		if (response.statusCode() < 200 || response.statusCode() >= 300) {
			throw new IOException("HTTP " + response.statusCode() + " for " + url);
		}
		return response.body();
	}

	public Response postJson(String url, String body, String bearer, int timeoutSeconds)
			throws IOException, InterruptedException {
		HttpRequest.Builder builder = HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(Math.max(5, timeoutSeconds)))
				.header("User-Agent", USER_AGENT)
				.header("Content-Type", "application/json")
				.header("Accept", "application/json")
				.POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8));
		if (bearer != null && !bearer.isBlank()) {
			builder.header("Authorization", "Bearer " + bearer);
		}
		HttpResponse<String> response =
				client.send(builder.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
		return new Response(response.statusCode(), response.body());
	}

	public record Response(int code, String body) {
		public boolean ok() {
			return code >= 200 && code < 300;
		}
	}
}
