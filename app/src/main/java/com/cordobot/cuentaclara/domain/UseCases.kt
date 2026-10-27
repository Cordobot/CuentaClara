package com.cordobot.cuentaclara.domain

import com.cordobot.cuentaclara.domain.model.Expense
import com.cordobot.cuentaclara.domain.model.User
import java.time.Instant
import javax.inject.Inject

class ValidationException(message: String) : Exception(message)

private val EMAIL = Regex("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$")

class SignInUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val session: SessionStore
) {
    suspend operator fun invoke(email: String, password: String): Result<User> {
        val cleanEmail = email.trim().lowercase()
        if (!EMAIL.matches(cleanEmail)) return Result.failure(ValidationException("Correo no válido"))
        if (password.isEmpty()) return Result.failure(ValidationException("Escribe tu contraseña"))
        return auth.signIn(cleanEmail, password).onSuccess { session.lastEmail = cleanEmail }
    }
}

class SignUpUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val session: SessionStore
) {
    suspend operator fun invoke(name: String, email: String, password: String): Result<User> {
        val cleanName = name.trim()
        val cleanEmail = email.trim().lowercase()
        return when {
            cleanName.length < 2 -> Result.failure(ValidationException("Escribe tu nombre"))
            !EMAIL.matches(cleanEmail) -> Result.failure(ValidationException("Correo no válido"))
            password.length < 8 -> Result.failure(ValidationException("La contraseña debe tener al menos 8 caracteres"))
            password.none { it.isDigit() } || password.none { it.isLetter() } ->
                Result.failure(ValidationException("Usa letras y números en la contraseña"))
            else -> auth.signUp(cleanName, cleanEmail, password).onSuccess { session.lastEmail = cleanEmail }
        }
    }
}

class SignOutUseCase @Inject constructor(
    private val auth: AuthRepository,
    private val session: SessionStore
) {
    suspend operator fun invoke() {
        auth.signOut()
        session.lastGroupId = null
    }
}

class AddExpenseUseCase @Inject constructor(private val groups: GroupRepository) {
    /** [amountText] is what the user typed: "45.000", "45000" or "12,50". */
    suspend operator fun invoke(
        groupId: String,
        description: String,
        amountText: String,
        paidBy: String,
        splitAmong: List<String>,
        createdBy: String
    ): Result<Unit> {
        val text = description.trim()
        val cents = parseAmountToCents(amountText)
        return when {
            text.isEmpty() -> Result.failure(ValidationException("Describe el gasto"))
            cents == null || cents <= 0 -> Result.failure(ValidationException("Monto no válido"))
            splitAmong.isEmpty() -> Result.failure(ValidationException("Elige entre quiénes se divide"))
            else -> groups.addExpense(
                groupId,
                Expense("", text, cents, paidBy, splitAmong, createdBy, Instant.now())
            )
        }
    }
}

/**
 * Parses amounts as written in Colombia: "." separates thousands, "," decimals.
 * A single "." followed by 1-2 digits is also accepted as decimals ("12.5").
 */
fun parseAmountToCents(text: String): Long? {
    val clean = text.trim().replace(" ", "").replace("$", "")
    if (clean.isEmpty()) return null
    val normalized = when {
        clean.contains(',') -> clean.replace(".", "").replace(',', '.')
        Regex("^\\d+\\.\\d{1,2}$").matches(clean) -> clean
        else -> clean.replace(".", "")
    }
    val value = normalized.toBigDecimalOrNull() ?: return null
    return value.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).toLong()
}

/** Invite codes avoid look-alike characters (0/O, 1/I/L) so they are easy to dictate. */
fun generateInviteCode(random: kotlin.random.Random = kotlin.random.Random.Default): String {
    val alphabet = "ABCDEFGHJKMNPQRSTUVWXYZ23456789"
    return (1..6).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
}
