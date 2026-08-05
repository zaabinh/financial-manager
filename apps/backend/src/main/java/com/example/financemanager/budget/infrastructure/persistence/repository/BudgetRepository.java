package com.example.financemanager.budget.infrastructure.persistence.repository;

import com.example.financemanager.budget.infrastructure.persistence.entity.Budget;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface BudgetRepository extends JpaRepository<Budget, UUID> {
}
