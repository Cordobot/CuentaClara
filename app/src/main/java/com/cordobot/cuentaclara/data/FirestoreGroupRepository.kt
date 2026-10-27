package com.cordobot.cuentaclara.data

import com.cordobot.cuentaclara.domain.GroupRepository
import com.cordobot.cuentaclara.domain.generateInviteCode
import com.cordobot.cuentaclara.domain.model.Expense
import com.cordobot.cuentaclara.domain.model.Group
import com.cordobot.cuentaclara.domain.model.Member
import com.cordobot.cuentaclara.domain.model.User
import com.cordobot.cuentaclara.domain.retryWithBackoff
import com.google.android.gms.tasks.Task
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.MetadataChanges
import com.google.firebase.firestore.Query
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton

/**
 * groups/{id}: name, emoji, inviteCode, memberIds, memberNames{uid: name}, createdBy, createdAt
 * groups/{id}/expenses/{id}: description, amountCents, paidBy, splitAmong, createdBy, createdAt
 * invites/{code}: groupId
 */
@Singleton
class FirestoreGroupRepository @Inject constructor(
    private val db: FirebaseFirestore
) : GroupRepository {

    private val groups = db.collection("groups")

    override fun observeGroups(uid: String): Flow<List<Group>> = callbackFlow {
        val registration = groups.whereArrayContains("memberIds", uid)
            .addSnapshotListener { snapshot, error ->
                if (error != null) close(error) else trySend(snapshot!!.documents.mapNotNull { it.toGroup() }.sortedBy { it.name })
            }
        awaitClose { registration.remove() }
    }

    override fun observeGroup(groupId: String): Flow<Group?> = callbackFlow {
        val registration = groups.document(groupId).addSnapshotListener { snapshot, error ->
            if (error != null) close(error) else trySend(snapshot?.toGroup())
        }
        awaitClose { registration.remove() }
    }

    // Includes local writes not yet confirmed by the server, flagged as pending
    override fun observeExpenses(groupId: String): Flow<List<Expense>> = callbackFlow {
        val registration = groups.document(groupId).collection("expenses")
            .orderBy("createdAt", Query.Direction.DESCENDING)
            .addSnapshotListener(MetadataChanges.INCLUDE) { snapshot, error ->
                if (error != null) close(error) else trySend(snapshot!!.documents.mapNotNull { it.toExpense() })
            }
        awaitClose { registration.remove() }
    }

    override suspend fun createGroup(name: String, emoji: String, owner: User): Result<String> = firestoreCall {
        val code = generateInviteCode()
        val ref = groups.document()
        ref.set(
            mapOf(
                "name" to name,
                "emoji" to emoji,
                "inviteCode" to code,
                "memberIds" to listOf(owner.uid),
                "memberNames" to mapOf(owner.uid to owner.name),
                "createdBy" to owner.uid,
                "createdAt" to FieldValue.serverTimestamp()
            )
        ).awaitWrite()
        db.collection("invites").document(code).set(mapOf("groupId" to ref.id)).awaitWrite()
        ref.id
    }

    override suspend fun joinGroup(inviteCode: String, user: User): Result<String> = firestoreCall {
        val code = inviteCode.trim().uppercase()
        val invite = db.collection("invites").document(code).get().await()
        val groupId = invite.getString("groupId") ?: throw IllegalArgumentException("Código no encontrado")
        // The security rules allow a non-member to add only themselves
        groups.document(groupId).update(
            mapOf(
                "memberIds" to FieldValue.arrayUnion(user.uid),
                "memberNames.${user.uid}" to user.name
            )
        ).awaitWrite()
        groupId
    }

    override suspend fun addExpense(groupId: String, expense: Expense): Result<Unit> = firestoreCall {
        groups.document(groupId).collection("expenses").document().set(
            mapOf(
                "description" to expense.description,
                "amountCents" to expense.amountCents,
                "paidBy" to expense.paidBy,
                "splitAmong" to expense.splitAmong,
                "createdBy" to expense.createdBy,
                "createdAt" to Timestamp.now()
            )
        ).awaitWrite()
    }

    override suspend fun deleteExpense(groupId: String, expenseId: String): Result<Unit> = firestoreCall {
        groups.document(groupId).collection("expenses").document(expenseId).delete().awaitWrite()
    }

    /**
     * Offline, Firestore applies writes to the local cache at once and syncs later, but the task
     * only completes when the server confirms. Waiting a few seconds keeps errors visible online
     * without blocking the UI offline: the write stays queued and shows as pending.
     */
    private suspend fun <T> Task<T>.awaitWrite() {
        withTimeoutOrNull(WRITE_CONFIRMATION_TIMEOUT_MS) { await() }
    }

    private suspend fun <T> firestoreCall(block: suspend () -> T): Result<T> = try {
        Result.success(retryWithBackoff(isTransient = ::isTransient) { block() })
    } catch (e: FirebaseFirestoreException) {
        Result.failure(Exception(if (e.code == FirebaseFirestoreException.Code.PERMISSION_DENIED) "No tienes permiso para esto" else "Error al sincronizar (${e.code})"))
    } catch (e: IllegalArgumentException) {
        Result.failure(Exception(e.message))
    } catch (e: FirebaseNetworkException) {
        Result.failure(Exception("Sin conexión"))
    }

    private fun isTransient(error: Throwable) = error is FirebaseNetworkException ||
        (error is FirebaseFirestoreException && error.code in TRANSIENT_CODES)

    private companion object {
        const val WRITE_CONFIRMATION_TIMEOUT_MS = 4_000L
        val TRANSIENT_CODES = setOf(
            FirebaseFirestoreException.Code.UNAVAILABLE,
            FirebaseFirestoreException.Code.DEADLINE_EXCEEDED,
            FirebaseFirestoreException.Code.ABORTED
        )
    }
}

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.toGroup(): Group? {
    if (!exists()) return null
    val ids = get("memberIds") as? List<String> ?: return null
    val names = get("memberNames") as? Map<String, String> ?: emptyMap()
    return Group(
        id = id,
        name = getString("name").orEmpty(),
        emoji = getString("emoji") ?: "💸",
        inviteCode = getString("inviteCode").orEmpty(),
        members = ids.map { Member(it, names[it] ?: "Miembro") },
        createdBy = getString("createdBy").orEmpty()
    )
}

@Suppress("UNCHECKED_CAST")
private fun DocumentSnapshot.toExpense(): Expense? {
    val amount = getLong("amountCents") ?: return null
    return Expense(
        id = id,
        description = getString("description").orEmpty(),
        amountCents = amount,
        paidBy = getString("paidBy").orEmpty(),
        splitAmong = get("splitAmong") as? List<String> ?: emptyList(),
        createdBy = getString("createdBy").orEmpty(),
        createdAt = (getTimestamp("createdAt") ?: Timestamp.now()).toDate().toInstant(),
        isPending = metadata.hasPendingWrites()
    )
}
