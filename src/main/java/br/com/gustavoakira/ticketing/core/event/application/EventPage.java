package br.com.gustavoakira.ticketing.core.event.application;

import java.util.List;

public record EventPage(List<EventResult> content, int page, int size,
                        long totalElements, int totalPages) {}
