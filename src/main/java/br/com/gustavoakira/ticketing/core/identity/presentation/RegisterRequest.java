package br.com.gustavoakira.ticketing.core.identity.presentation;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Map;
import java.util.Set;
public record RegisterRequest(String name, String email, String password) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static RegisterRequest from(Map<String, Object> fields) {
        RequestFields.requireKnown(fields, Set.of("name", "email", "password"));
        return new RegisterRequest(RequestFields.text(fields, "name"), RequestFields.text(fields, "email"), RequestFields.text(fields, "password"));
    }
    @Override public String toString() { return "RegisterRequest[REDACTED]"; }
}
