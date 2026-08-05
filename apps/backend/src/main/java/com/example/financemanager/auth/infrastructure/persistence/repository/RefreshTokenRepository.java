package com.example.financemanager.auth.infrastructure.persistence.repository;

import com.example.financemanager.auth.infrastructure.persistence.entity.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
}
