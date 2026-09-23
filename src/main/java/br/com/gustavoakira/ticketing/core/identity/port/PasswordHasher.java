package br.com.gustavoakira.ticketing.core.identity.port;
public interface PasswordHasher {
    String hash(String password);
    boolean matches(String password, String encoded);
}
