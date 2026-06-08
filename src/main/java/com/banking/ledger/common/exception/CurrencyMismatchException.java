package com.banking.ledger.common.exception;

public class CurrencyMismatchException extends RuntimeException {
    public CurrencyMismatchException(String from, String to) {
        super("Currency mismatch: " + from + " vs " + to);
    }
}
