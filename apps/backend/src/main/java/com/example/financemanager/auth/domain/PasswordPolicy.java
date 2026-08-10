package com.example.financemanager.auth.domain;

import com.example.financemanager.shared.error.UnprocessableEntityException;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.ArrayList;

@Component
public class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 50;

    public void validate(String password, String username) {

        if(password == null || password.isBlank())
            throw violation("Password must not be blank");

        List<String> violations = new ArrayList<>();

        long length = password.codePoints().count();

        if(length < MIN_LENGTH) {
            violations.add("Password must be longer than" + MIN_LENGTH + "characters");
        }

        if(length > MAX_LENGTH) {
            violations.add("Password is too long.");
        }

        if(password.codePoints().noneMatch(Character::isUpperCase)) {
            violations.add("Password must contain a uppercase letter");
        }

        
        if(password.codePoints().noneMatch(Character::isLowerCase)) {
            violations.add("Password must contain a lowercase letter");
        }

        
        if(password.codePoints().noneMatch(Character::isDigit)) {
            violations.add("Password must contain a digit");
        }

        if(password.codePoints().allMatch(Character::isLetterOrDigit)) {
            violations.add("Password must contain a special character");
        }

        if(containUsername(password, username)) {
            violations.add("Password must not contain username");
        }

        String finalViolations = "Password is invalid: \n" + String.join("\n", violations);

        if(!violations.isEmpty()) {
            throw violation(finalViolations);
        }
    }

    private boolean containUsername(
        String password, 
        String username
    ) {
        if(username == null || username.isBlank()) {
            return false;
        }
        
        return password.toLowerCase(Locale.ROOT).contains(username.toLowerCase(Locale.ROOT));
    }

    private UnprocessableEntityException violation(String message) {
        return new UnprocessableEntityException("PASSWORD_POLICY_VIOLATION", message);  
    }
}
