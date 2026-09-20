package br.com.gustavoakira.ticketing.core.event.port;

import java.util.List;

public record PageResult<T>(List<T> content, long totalElements, int totalPages) {
    public PageResult {
        content = List.copyOf(content);
    }
}