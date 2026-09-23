package br.com.gustavoakira.ticketing.core.identity.presentation;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Map;
import java.util.Set;
public record LoginRequest(String email, String password) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static LoginRequest from(Map<String, Object> fields) {
        RequestFields.requireKnown(fields, Set.of("email", "password"));
        return new LoginRequest(RequestFields.text(fields, "email"), RequestFields.text(fields, "password"));
    }
    public LoginRequest {
        if (email == null || password == null || email.length() > 1024 || password.length() > 1024)
            throw new IllegalArgumentException("email and password are required and must not exceed 1024 characters");
    }
    @Override public String toString() { return "LoginRequest[REDACTED]"; }
}
