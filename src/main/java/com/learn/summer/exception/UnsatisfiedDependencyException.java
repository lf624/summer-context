package com.learn.summer.exception;

public class UnsatisfiedDependencyException extends BeanCreationException{
    public UnsatisfiedDependencyException() {
    }

    public UnsatisfiedDependencyException(String message) {
        super(message);
    }

    public UnsatisfiedDependencyException(Throwable cause) {
        super(cause);
    }

    public UnsatisfiedDependencyException(String message, Throwable cause) {
        super(message, cause);
    }
}
