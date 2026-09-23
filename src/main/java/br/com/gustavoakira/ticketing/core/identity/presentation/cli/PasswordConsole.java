package br.com.gustavoakira.ticketing.core.identity.presentation.cli;
@FunctionalInterface
public interface PasswordConsole { char[] readPassword(String prompt); }
