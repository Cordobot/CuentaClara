package com.cordobot.cuentaclara.data

import com.cordobot.cuentaclara.domain.AuthRepository
import com.cordobot.cuentaclara.domain.model.User
import com.google.firebase.FirebaseNetworkException
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseAuthException
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.auth.userProfileChangeRequest
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.SetOptions
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.tasks.await
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class FirebaseAuthRepository @Inject constructor(
    private val auth: FirebaseAuth,
    private val firestore: FirebaseFirestore
) : AuthRepository {

    // Firebase keeps the session (refresh token) on the device; this only listens to it
    override val currentUser: Flow<User?> = callbackFlow {
        val listener = FirebaseAuth.AuthStateListener { trySend(it.currentUser?.toUser()) }
        auth.addAuthStateListener(listener)
        awaitClose { auth.removeAuthStateListener(listener) }
    }

    override suspend fun signIn(email: String, password: String): Result<User> = authCall {
        auth.signInWithEmailAndPassword(email, password).await().user!!.toUser()
    }

    override suspend fun signUp(name: String, email: String, password: String): Result<User> = authCall {
        val user = auth.createUserWithEmailAndPassword(email, password).await().user!!
        user.updateProfile(userProfileChangeRequest { displayName = name }).await()
        firestore.collection("users").document(user.uid)
            .set(mapOf("name" to name, "email" to email), SetOptions.merge())
            .await()
        User(user.uid, name, email)
    }

    override suspend fun signOut() = auth.signOut()

    private inline fun authCall(block: () -> User): Result<User> = try {
        Result.success(block())
    } catch (e: FirebaseNetworkException) {
        Result.failure(Exception("Sin conexión. Revisa tu internet e inténtalo de nuevo."))
    } catch (e: FirebaseAuthException) {
        Result.failure(Exception(authMessage(e.errorCode)))
    }

    private fun authMessage(code: String) = when (code) {
        "ERROR_INVALID_CREDENTIAL", "ERROR_WRONG_PASSWORD", "ERROR_USER_NOT_FOUND", "ERROR_INVALID_EMAIL" ->
            "Correo o contraseña incorrectos"
        "ERROR_EMAIL_ALREADY_IN_USE" -> "Ya existe una cuenta con ese correo"
        "ERROR_WEAK_PASSWORD" -> "La contraseña es muy débil"
        "ERROR_TOO_MANY_REQUESTS" -> "Demasiados intentos. Espera un momento."
        "ERROR_USER_DISABLED" -> "Esta cuenta está deshabilitada"
        else -> "No se pudo iniciar sesión ($code)"
    }
}

private fun FirebaseUser.toUser() = User(uid, displayName ?: email?.substringBefore('@') ?: "Usuario", email.orEmpty())
