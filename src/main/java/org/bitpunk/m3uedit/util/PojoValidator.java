package org.bitpunk.m3uedit.util;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

import java.util.Set;

public class PojoValidator<T> {

    public Set<ConstraintViolation<T>> validate(T pojo) {
        Validator validator;
        try (ValidatorFactory factory = Validation.buildDefaultValidatorFactory()) {
            validator = factory.getValidator();
        }
        return validator.validate(pojo);
    }

}