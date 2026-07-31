package org.ome.converter.service.validation;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.ome.converter.core.exception.ValidationException;

import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class StorageValidatorTest {

    @Test
    void testValidateTargetDestinationValid(@TempDir Path tempDir) {
        StorageValidator validator = new StorageValidator();
        assertThatCode(() -> validator.validateTargetDestination(tempDir, 1024 * 1024L))
            .doesNotThrowAnyException();
    }

    @Test
    void testValidateTargetDestinationNull() {
        StorageValidator validator = new StorageValidator();
        assertThatThrownBy(() -> validator.validateTargetDestination(null, 100))
            .isInstanceOf(ValidationException.class)
            .hasMessageContaining("cannot be null");
    }
}
