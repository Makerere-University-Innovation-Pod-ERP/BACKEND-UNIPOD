package org.unipod.unipoderpbackend.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import java.net.URI;
import java.net.URISyntaxException;

public class HttpsUrlValidator implements ConstraintValidator<HttpsUrl, String> {
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null || value.isBlank()) {
            return true;
        }
        try {
            URI uri = new URI(value.trim());
            String host = uri.getHost();
            return uri.isAbsolute()
                    && "https".equalsIgnoreCase(uri.getScheme())
                    && host != null
                    && !host.isBlank();
        } catch (URISyntaxException exception) {
            return false;
        }
    }
}
