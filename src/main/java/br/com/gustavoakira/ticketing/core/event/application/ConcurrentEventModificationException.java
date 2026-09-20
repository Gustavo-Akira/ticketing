package br.com.gustavoakira.ticketing.core.event.application;

public class ConcurrentEventModificationException extends RuntimeException {
    public ConcurrentEventModificationException(String conflictUpdateNotMerged) {
        super(conflictUpdateNotMerged);
    }
}
