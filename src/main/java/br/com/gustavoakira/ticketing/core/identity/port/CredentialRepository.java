package br.com.gustavoakira.ticketing.core.identity.port;
import java.util.Optional;
import java.util.UUID;
public interface CredentialRepository {
    void save(UUID userId, String passwordHash);
    Optional<String> findHash(UUID userId);
}
