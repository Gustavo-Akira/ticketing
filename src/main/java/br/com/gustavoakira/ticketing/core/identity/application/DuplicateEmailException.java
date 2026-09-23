package br.com.gustavoakira.ticketing.core.identity.application;
public class DuplicateEmailException extends RuntimeException {
    public DuplicateEmailException() { super("Email already registered"); }
}
