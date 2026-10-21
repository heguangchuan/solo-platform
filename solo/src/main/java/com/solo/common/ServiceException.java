package com.solo.common;

import lombok.Getter;

@Getter
public class ServiceException extends RuntimeException {

    private int code;

    public ServiceException() {
        super();
    }

    public ServiceException(String message) {
        super(message);
        this.code = 500;
    }

    public ServiceException(int code, String message) {
        super(message);
        this.code = code;
    }

    public ServiceException(String message, Throwable cause) {
        super(message, cause);
        this.code = 500;
    }

    public ServiceException(int code, String message, Throwable cause) {
        super(message, cause);
        this.code = code;
    }

}
