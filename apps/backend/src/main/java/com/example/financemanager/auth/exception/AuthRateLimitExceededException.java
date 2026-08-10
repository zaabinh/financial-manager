package com.example.financemanager.auth.exception;

import com.example.financemanager.shared.error.ApiException;
import org.springframework.http.HttpStatus;

public class AuthRateLimitExceededException extends ApiException {

    public AuthRateLimitExceededException() {
        super(
                HttpStatus.TOO_MANY_REQUESTS,
                "AUTH_RATE_LIMIT_EXCEEDED",
                "Too many authentication attempts"
        );
    }
}
