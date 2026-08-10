package com.example.financemanager.auth.exception;

import com.example.financemanager.shared.error.ConflictException;

public class UserAlreadyExistsException
extends ConflictException {
    public UserAlreadyExistsException(String message) {
        super("USER_ALREADY_EXISTS", message);
    }
}
