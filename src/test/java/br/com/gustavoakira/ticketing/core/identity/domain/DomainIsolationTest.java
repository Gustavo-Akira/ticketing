package br.com.gustavoakira.ticketing.core.identity.domain;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class DomainIsolationTest {
    @ParameterizedTest
    @ValueSource(classes = {User.class, UserDetails.class, Role.class, PasswordPolicy.class, RefreshSession.class, RefreshToken.class})
    void domainDoesNotDependOnFrameworks(Class<?> model) {
        var annotations = Stream.concat(Arrays.stream(model.getAnnotations()),
                Stream.concat(Arrays.stream(model.getDeclaredFields()).flatMap(f -> Arrays.stream(f.getAnnotations())),
                        Arrays.stream(model.getDeclaredMethods()).flatMap(m -> Arrays.stream(m.getAnnotations()))));
        var types = Stream.concat(Arrays.stream(model.getDeclaredFields()).map(f -> f.getGenericType().getTypeName()),
                Arrays.stream(model.getDeclaredMethods()).flatMap(m -> Stream.concat(
                        Stream.of(m.getGenericReturnType().getTypeName()),
                        Arrays.stream(m.getGenericParameterTypes()).map(t -> t.getTypeName()))));
        assertThat(Stream.concat(annotations.map(Annotation::annotationType).map(Class::getName), types))
                .noneMatch(name -> name.contains("jakarta.persistence.") || name.contains("org.hibernate.")
                        || name.contains("org.springframework."));
    }
}
