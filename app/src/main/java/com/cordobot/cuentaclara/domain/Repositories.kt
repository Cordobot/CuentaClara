package com.cordobot.cuentaclara.domain

import com.cordobot.cuentaclara.domain.model.ConnectionState
import com.cordobot.cuentaclara.domain.model.Expense
import com.cordobot.cuentaclara.domain.model.Group
import com.cordobot.cuentaclara.domain.model.User
import kotlinx.coroutines.flow.Flow

interface AuthRepository {
    /** The signed-in user, or null; emits on sign in and sign out. */
    val currentUser: Flow<User?>
    suspend fun signIn(email: String, password: String): Result<User>
    suspend fun signUp(name: String, email: String, password: String): Result<User>
    suspend fun signOut()
}

interface GroupRepository {
    fun observeGroups(uid: String): Flow<List<Group>>
    fun observeGroup(groupId: String): Flow<Group?>
    fun observeExpenses(groupId: String): Flow<List<Expense>>
    suspend fun createGroup(name: String, emoji: String, owner: User): Result<String>
    suspend fun joinGroup(inviteCode: String, user: User): Result<String>
    suspend fun addExpense(groupId: String, expense: Expense): Result<Unit>
    suspend fun deleteExpense(groupId: String, expenseId: String): Result<Unit>
}

/** Small pieces of the session kept on the device, encrypted at rest. */
interface SessionStore {
    var lastEmail: String?
    var lastGroupId: String?
    fun clear()
}

interface ConnectivityMonitor {
    val state: Flow<ConnectionState>
}
