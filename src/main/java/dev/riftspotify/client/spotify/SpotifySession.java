package dev.riftspotify.client.spotify;

public final class SpotifySession {
    private String accessToken;
    private String refreshToken;
    private long expiresAt;

    public boolean connected() { return accessToken != null && !accessToken.isBlank(); }
    public String accessToken() { return accessToken; }
    public String refreshToken() { return refreshToken; }
    public long expiresAt() { return expiresAt; }
    public void set(String accessToken, String refreshToken, long expiresInSeconds) {
        this.accessToken = accessToken;
        if (refreshToken != null && !refreshToken.isBlank()) this.refreshToken = refreshToken;
        this.expiresAt = System.currentTimeMillis() + expiresInSeconds * 1000L;
    }
    public void clear() { accessToken = null; refreshToken = null; expiresAt = 0; }
    public boolean expiresSoon() { return !connected() || System.currentTimeMillis() > expiresAt - 60_000; }
}
