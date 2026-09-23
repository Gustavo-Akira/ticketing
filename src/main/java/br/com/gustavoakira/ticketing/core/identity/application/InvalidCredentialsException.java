package br.com.gustavoakira.ticketing.core.identity.application;
public class InvalidCredentialsException extends RuntimeException {
    public InvalidCredentialsException() { super("Invalid credentials"); }
}
