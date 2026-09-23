package br.com.gustavoakira.ticketing.core.identity.domain;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class PasswordPolicyTest {
    @Test void rejectsInvalidLengthsWithoutTruncatingUnicode() {
        for (String value : new String[] {null, "", "a".repeat(11), "a".repeat(65), "é".repeat(37)}) {
            assertThatThrownBy(() -> PasswordPolicy.validate(value)).isInstanceOf(IllegalArgumentException.class);
        }
        for (String value : new String[] {"a".repeat(12), "a".repeat(64), "é".repeat(36), "😀".repeat(18), "  password  "}) {
            assertThatCode(() -> PasswordPolicy.validate(value)).doesNotThrowAnyException();
        }
    }
}
