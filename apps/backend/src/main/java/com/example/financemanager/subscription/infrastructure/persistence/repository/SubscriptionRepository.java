package com.example.financemanager.subscription.infrastructure.persistence.repository;

import com.example.financemanager.subscription.infrastructure.persistence.entity.Subscription;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface SubscriptionRepository extends JpaRepository<Subscription, UUID> {
}
