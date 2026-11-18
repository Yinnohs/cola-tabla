package com.kanbanlive.sharedkernel;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.kanbanlive.sharedkernel.domain.ValidationException;
import com.kanbanlive.sharedkernel.domain.Validations;
import org.junit.jupiter.api.Test;

class ValidationsTest {

    @Test
    void trimsValidText() {
        assertThat(Validations.requireText("  hi  ", "name", 5)).isEqualTo("hi");
    }

    @Test
    void rejectsNullBlankAndTooLong() {
        assertThatThrownBy(() -> Validations.requireText(null, "name", 5)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Validations.requireText("   ", "name", 5)).isInstanceOf(ValidationException.class);
        assertThatThrownBy(() -> Validations.requireText("123456", "name", 5))
                .isInstanceOf(ValidationException.class).hasMessageContaining("at most 5");
    }
}
