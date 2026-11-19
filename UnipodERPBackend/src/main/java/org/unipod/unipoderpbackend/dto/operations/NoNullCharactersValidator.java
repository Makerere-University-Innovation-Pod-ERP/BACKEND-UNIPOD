package org.unipod.unipoderpbackend.dto.operations;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;

public class NoNullCharactersValidator implements ConstraintValidator<NoNullCharacters, CharSequence> {
    @Override
    public boolean isValid(CharSequence value, ConstraintValidatorContext context) {
        return value == null || value.chars().noneMatch(character -> character == 0);
    }
}
