package com.varista.exception;

import java.io.IOException;

public class InvoiceFileNotFoundException extends IOException {

    public InvoiceFileNotFoundException(String message) {
        super(message);
    }
}
