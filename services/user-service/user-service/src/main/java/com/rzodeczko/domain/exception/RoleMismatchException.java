package com.rzodeczko.domain.exception;

public class RoleMismatchException extends RuntimeException {
    public RoleMismatchException(String headerRole, String dbRole) {
        super("Role mismatch detected: header=%s, database=%s. Possible token desynchronization."
                .formatted(headerRole, dbRole));
    }
}
