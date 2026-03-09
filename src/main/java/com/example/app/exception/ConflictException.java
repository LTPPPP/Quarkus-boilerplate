package com.example.app.exception;

public class ConflictException extends RuntimeException {

    private final String resourceType;
    private final String conflictField;

    public ConflictException(String resourceType, String conflictField) {
        super(String.format("%s already exists with conflicting field: %s", resourceType, conflictField));
        this.resourceType = resourceType;
        this.conflictField = conflictField;
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getConflictField() {
        return conflictField;
    }
}
