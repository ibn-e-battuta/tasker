package io.shinmen.app.tasker.exception;

import org.springframework.http.HttpStatus;

import lombok.Getter;

@Getter
public class CustomException extends RuntimeException {
    private final HttpStatus status;
    private final Object data;

    public CustomException(String message, HttpStatus status) {
        super(message);
        this.status = status;
        this.data = null;
    }

    public CustomException(String message, HttpStatus status, Object data) {
        super(message);
        this.status = status;
        this.data = data;
    }
}
