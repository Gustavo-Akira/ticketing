package br.com.gustavoakira.ticketing.core.identity.presentation.cli;
import br.com.gustavoakira.ticketing.core.identity.application.*;
import br.com.gustavoakira.ticketing.core.identity.domain.*;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class CreateAdminCommand {
    private final CreateAccountUseCase accounts;
    private final PasswordConsole console;
    public CreateAdminCommand(CreateAccountUseCase accounts, PasswordConsole console) {
        this.accounts = accounts; this.console = console;
    }
    public int execute(String[] args) {
        char[] password = null;
        char[] confirmation = null;
        try {
            var options = parse(args);
            // Validate public account details before prompting for a secret.
            new UserDetails(options.get("--name"), options.get("--email"), Set.of(Role.ADMIN));
            password = console.readPassword("Password: ");
            confirmation = console.readPassword("Confirm password: ");
            if (password == null || confirmation == null || !Arrays.equals(password, confirmation)) {
                System.err.println("Password confirmation failed");
                return 2;
            }
            var user = accounts.execute(options.get("--name"), options.get("--email"), new String(password), Set.of(Role.ADMIN));
            System.out.println("ADMIN created: " + user.getId());
            return 0;
        } catch (DuplicateEmailException failure) {
            System.err.println("Email already registered; existing account was not changed");
            return 2;
        } catch (IllegalArgumentException | IllegalStateException failure) {
            System.err.println("Invalid command, account details or password; an interactive console is required");
            return 2;
        } catch (RuntimeException failure) {
            System.err.println("ADMIN creation failed");
            return 1;
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }
    private Map<String, String> parse(String[] args) {
        if (args.length != 6 || !"identity".equals(args[0]) || !"create-admin".equals(args[1]))
            throw new IllegalArgumentException("Expected identity create-admin --name <name> --email <email>");
        var options = new HashMap<String, String>();
        for (int i = 2; i < args.length; i += 2) {
            if (!Set.of("--name", "--email").contains(args[i]) || options.putIfAbsent(args[i], args[i + 1]) != null)
                throw new IllegalArgumentException("Invalid or duplicate option");
        }
        return options;
    }
}
