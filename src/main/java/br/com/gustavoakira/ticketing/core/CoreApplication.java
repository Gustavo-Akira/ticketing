package br.com.gustavoakira.ticketing.core;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.WebApplicationType;
import br.com.gustavoakira.ticketing.core.identity.presentation.cli.CreateAdminCommand;

@SpringBootApplication
public class CoreApplication {

	public static void main(String[] args) {
		if (args.length > 0 && "identity".equals(args[0])) {
			System.exit(runAdmin(args));
			return;
		}
		SpringApplication.run(CoreApplication.class, args);
	}

	public static int runAdmin(String[] args) {
		var application = new SpringApplication(CoreApplication.class);
		application.setWebApplicationType(WebApplicationType.NONE);
		try (var context = application.run()) {
			return context.getBean(CreateAdminCommand.class).execute(args);
		} catch (RuntimeException failure) {
			System.err.println("Cannot initialize administrative command");
			return 1;
		}
	}


}
