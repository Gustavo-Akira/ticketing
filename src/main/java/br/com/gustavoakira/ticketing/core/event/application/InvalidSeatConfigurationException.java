package br.com.gustavoakira.ticketing.core.event.application;

public class InvalidSeatConfigurationException extends RuntimeException{
    public InvalidSeatConfigurationException(String s) {
        super(s);
    }
}
