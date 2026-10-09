package br.com.fiap.delivery.order.exception;

public class TooManyRequestsException extends RuntimeException {

    public TooManyRequestsException(String message) {
        super(message);
    }
}