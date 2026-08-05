package com.example.financemanager.category.infrastructure.persistence.repository;

import com.example.financemanager.category.infrastructure.persistence.entity.Category;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<Category, UUID> {
}
