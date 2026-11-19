package org.unipod.unipoderpbackend.dto.operations;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Free text must not hold the NUL character: PostgreSQL rejects it, and the failure would otherwise be a 500. */
@Documented
@Constraint(validatedBy = NoNullCharactersValidator.class)
@Target({ElementType.METHOD, ElementType.FIELD, ElementType.ANNOTATION_TYPE, ElementType.CONSTRUCTOR,
        ElementType.PARAMETER, ElementType.TYPE_USE})
@Retention(RetentionPolicy.RUNTIME)
public @interface NoNullCharacters {
    String message() default "must not contain null characters";

    Class<?>[] groups() default {};

    Class<? extends Payload>[] payload() default {};
}
