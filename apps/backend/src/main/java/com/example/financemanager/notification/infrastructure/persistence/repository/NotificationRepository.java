package com.example.financemanager.notification.infrastructure.persistence.repository;

import com.example.financemanager.notification.infrastructure.persistence.entity.Notification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface NotificationRepository extends JpaRepository<Notification, UUID> {
}
