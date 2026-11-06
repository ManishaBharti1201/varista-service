package com.varista.exception;

import java.io.IOException;

public class InvalidAttendanceWorkbookException extends IOException {

    public InvalidAttendanceWorkbookException(String message, Throwable cause) {
        super(message, cause);
    }
}
