package org.bitpunk.m3uedit.plugins.support;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;

/**
 * Checks the Jakarta Validation constraints of the values entered in the dialogs of the built-in plugins.
 */
public final class BeanValidator {

    private BeanValidator() {
        // Utility class
    }

    public static <T> Set<ConstraintViolation<T>> validate(T bean) {
        Validator validator;
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
        return validator.validate(bean);
    }
}
