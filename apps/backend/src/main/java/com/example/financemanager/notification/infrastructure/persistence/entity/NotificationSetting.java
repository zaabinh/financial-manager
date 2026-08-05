package com.example.financemanager.notification.infrastructure.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;
import java.time.LocalTime;
import java.util.UUID;

@Entity
@Table(name = "notification_settings", schema = "finance")
@Getter
@NoArgsConstructor
public class NotificationSetting {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "user_id", nullable = false, unique = true)
    private UUID userId;

    @Column(name = "daily_reminder_enabled", nullable = false)
    private boolean dailyReminderEnabled = true;

    @Column(name = "daily_reminder_time", nullable = false)
    private LocalTime dailyReminderTime = LocalTime.of(20, 0);

    @Column(name = "budget_warning_enabled", nullable = false)
    private boolean budgetWarningEnabled = true;

    @Column(name = "budget_exceeded_enabled", nullable = false)
    private boolean budgetExceededEnabled = true;

    @Column(name = "system_notifications_enabled", nullable = false)
    private boolean systemNotificationsEnabled = true;

    @Column(name = "in_app_enabled", nullable = false)
    private boolean inAppEnabled = true;

    @Column(name = "email_enabled", nullable = false)
    private boolean emailEnabled;

    @Column(name = "push_enabled", nullable = false)
    private boolean pushEnabled;

    @Column(name = "quiet_hours_enabled", nullable = false)
    private boolean quietHoursEnabled;

    @Column(name = "quiet_hours_start")
    private LocalTime quietHoursStart;

    @Column(name = "quiet_hours_end")
    private LocalTime quietHoursEnd;

    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private Instant updatedAt;
}
