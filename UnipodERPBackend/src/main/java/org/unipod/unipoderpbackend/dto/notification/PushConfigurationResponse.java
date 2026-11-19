package org.unipod.unipoderpbackend.dto.notification;

public record PushConfigurationResponse(boolean enabled, String publicKey) {
}
