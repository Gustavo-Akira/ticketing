package br.com.gustavoakira.ticketing.core.event.application;

import java.util.List;

public record SeatPage(List<SeatResult> content, int page, int size,
                       long totalElements, int totalPages) {}
