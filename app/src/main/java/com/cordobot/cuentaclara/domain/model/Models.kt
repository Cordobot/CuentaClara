package com.cordobot.cuentaclara.domain.model

import java.time.Instant

data class User(val uid: String, val name: String, val email: String)

data class Member(val uid: String, val name: String)

data class Group(
    val id: String,
    val name: String,
    val emoji: String,
    val inviteCode: String,
    val members: List<Member>,
    val createdBy: String
) {
    fun nameOf(uid: String): String = members.firstOrNull { it.uid == uid }?.name ?: "Alguien"
}

/** Amounts are integer cents so splits never accumulate floating point errors. */
data class Expense(
    val id: String,
    val description: String,
    val amountCents: Long,
    val paidBy: String,
    val splitAmong: List<String>,
    val createdBy: String,
    val createdAt: Instant,
    // Saved on the device but not yet confirmed by the server (offline)
    val isPending: Boolean = false
)

/** [from] owes [to] the given amount. */
data class Settlement(val from: String, val to: String, val amountCents: Long)

sealed interface ConnectionState {
    data object Online : ConnectionState
    data object Offline : ConnectionState
}
