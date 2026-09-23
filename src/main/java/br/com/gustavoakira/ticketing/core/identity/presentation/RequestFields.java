package br.com.gustavoakira.ticketing.core.identity.presentation;

import java.util.Map;
import java.util.Set;

final class RequestFields {
    private RequestFields() {}
    static void requireKnown(Map<String, Object> fields, Set<String> allowed) {
        if (!allowed.containsAll(fields.keySet())) throw new IllegalArgumentException("Unknown request field");
    }
    static String text(Map<String, Object> fields, String name) {
        Object value = fields.get(name);
        if (value == null) return null;
        if (!(value instanceof String text)) throw new IllegalArgumentException("Request fields must be strings");
        return text;
    }
}
