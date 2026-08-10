package com.example.financemanager.user.infrastructure.persistence.repository;

import com.example.financemanager.user.infrastructure.persistence.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;
import java.util.Optional;

import com.example.financemanager.user.domain.UserStatus;

@Repository
public interface UserRepository extends JpaRepository<User, UUID> {
    Optional<User> findByUsernameIgnoreCase(String username);
    boolean existsByEmailIgnoreCase(String email);
    boolean existsByUsernameIgnoreCase(String username);
    Optional<User> findByEmailIgnoreCase(String email);
    Optional<User> findByIdAndStatusAndDeletedAtIsNull(UUID id, UserStatus status);
}
