package br.com.gustavoakira.ticketing.core.event.domain;

import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.stream.Stream;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.assertj.core.api.Assertions.assertThat;

class DomainIsolationTest {
    @ParameterizedTest
    @ValueSource(classes = {Event.class, Seat.class})
    void domainModelsDoNotDependOnPersistenceAnnotations(Class<?> model) {
        var annotations = Stream.concat(Arrays.stream(model.getAnnotations()),
                Arrays.stream(model.getDeclaredFields()).flatMap(field -> Arrays.stream(field.getAnnotations())));
        assertThat(annotations.map(Annotation::annotationType).map(Class::getName))
                .noneMatch(name -> name.startsWith("jakarta.persistence.") || name.startsWith("org.hibernate."));
    }
}
