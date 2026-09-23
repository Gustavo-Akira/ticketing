package br.com.gustavoakira.ticketing.core.identity.infrastructure;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
@Configuration
public class IdentityConfiguration {
    @Bean public Clock identityClock() { return Clock.systemUTC(); }
}
