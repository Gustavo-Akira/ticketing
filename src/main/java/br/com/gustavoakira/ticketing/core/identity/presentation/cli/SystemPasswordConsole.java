package br.com.gustavoakira.ticketing.core.identity.presentation.cli;
import org.springframework.stereotype.Component;
@Component
public class SystemPasswordConsole implements PasswordConsole {
    @Override public char[] readPassword(String prompt) {
        var console = System.console();
        if (console == null) throw new IllegalStateException("Interactive console required");
        return console.readPassword("%s", prompt);
    }
}
