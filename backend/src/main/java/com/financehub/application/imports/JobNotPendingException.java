package com.financehub.application.imports;

public class JobNotPendingException extends RuntimeException {
    public JobNotPendingException(String message) {
        super(message);
    }
}
