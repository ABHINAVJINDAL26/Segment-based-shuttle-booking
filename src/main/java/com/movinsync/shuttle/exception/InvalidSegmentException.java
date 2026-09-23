package com.movinsync.shuttle.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

@ResponseStatus(HttpStatus.BAD_REQUEST)
public class InvalidSegmentException extends RuntimeException {
    public InvalidSegmentException(String message) { super(message); }
}
