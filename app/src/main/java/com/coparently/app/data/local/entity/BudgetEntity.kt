package com.coparently.app.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.LocalDateTime

/**
 * Entity representing a budget in the local Room database.
 */
@Entity(tableName = "budgets")
data class BudgetEntity(
    @PrimaryKey
    val id: String,
    val childId: String? = null,
    val category: String, // Stored as string enum name
    val monthlyLimit: Double,
    val currency: String = "USD",
    val alertThreshold: Double = 0.8,
    val isActive: Boolean = true,
    val createdAt: LocalDateTime,
    val syncedToFirestore: Boolean = false
)
