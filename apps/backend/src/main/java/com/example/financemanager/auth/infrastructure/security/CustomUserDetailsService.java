package com.example.financemanager.auth.infrastructure.security;

import com.example.financemanager.user.infrastructure.persistence.entity.User;
import com.example.financemanager.user.domain.UserStatus;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.example.financemanager.user.infrastructure.persistence.repository.UserRepository;

import lombok.RequiredArgsConstructor;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService{
    private final UserRepository userRepository;

    @Override
    public UserDetails loadUserByUsername(
        String identifier
    ) throws UsernameNotFoundException {
        String normalizedIdentifier = identifier.trim();
        User user = userRepository
                    .findByUsernameIgnoreCase(normalizedIdentifier)
                    .orElseThrow(() ->
                new UsernameNotFoundException("User not found"));
        return new UserPrincipal(user);
    }

    public UserPrincipal loadActiveUserById(UUID userId) {
        User user = userRepository
                .findByIdAndStatusAndDeletedAtIsNull(userId, UserStatus.ACTIVE)
                .orElseThrow(() -> new UsernameNotFoundException("User not found"));
        return new UserPrincipal(user);
    }
}
