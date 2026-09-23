package br.com.gustavoakira.ticketing.core.identity.port;
public interface RefreshTokenGenerator {
    String generate();
    String digest(String rawToken);
}
