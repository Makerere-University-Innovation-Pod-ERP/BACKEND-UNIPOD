package org.unipod.unipoderpbackend.model.authorization;

public record AuthorizationDecision(boolean allowed, DenialCode code, String reason) {
    private static final AuthorizationDecision ALLOWED = new AuthorizationDecision(true, null, null);

    public static AuthorizationDecision allow() {
        return ALLOWED;
    }

    public static AuthorizationDecision deny(DenialCode code) {
        return new AuthorizationDecision(false, code, code.message());
    }
}