package br.com.gustavoakira.ticketing.core.identity.presentation;
import com.fasterxml.jackson.annotation.JsonCreator;
import java.util.Map;
import java.util.Set;
public record RefreshRequest(String refreshToken) {
    @JsonCreator(mode = JsonCreator.Mode.DELEGATING)
    public static RefreshRequest from(Map<String, Object> fields) {
        RequestFields.requireKnown(fields, Set.of("refreshToken"));
        return new RefreshRequest(RequestFields.text(fields, "refreshToken"));
    }
    public RefreshRequest {
        if (refreshToken == null || !refreshToken.matches("[A-Za-z0-9_-]{43}"))
            throw new IllegalArgumentException("A valid refreshToken is required");
    }
    @Override public String toString() { return "RefreshRequest[REDACTED]"; }
}
