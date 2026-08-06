package com.example.financemanager.auth.api.dto.response;
import java.util.UUID;

import com.example.financemanager.user.infrastructure.persistence.entity.User;
import com.example.financemanager.user.domain.UserRole;;

public record UserResponse(
    UUID id,
    String email,
    String username,
    String displayName,
    UserRole role
) {
    public static UserResponse from(User user) {
        return new UserResponse(
            user.getId(),
            user.getEmail(),
            user.getUsername(),
            user.getDisplayName(),
            user.getRole()
        );
    }
}