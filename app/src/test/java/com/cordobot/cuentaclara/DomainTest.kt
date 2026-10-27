package com.cordobot.cuentaclara

import com.cordobot.cuentaclara.domain.AddExpenseUseCase
import com.cordobot.cuentaclara.domain.AuthRepository
import com.cordobot.cuentaclara.domain.Balances
import com.cordobot.cuentaclara.domain.GroupRepository
import com.cordobot.cuentaclara.domain.SessionStore
import com.cordobot.cuentaclara.domain.SignUpUseCase
import com.cordobot.cuentaclara.domain.generateInviteCode
import com.cordobot.cuentaclara.domain.model.Expense
import com.cordobot.cuentaclara.domain.model.Settlement
import com.cordobot.cuentaclara.domain.model.User
import com.cordobot.cuentaclara.domain.parseAmountToCents
import com.cordobot.cuentaclara.domain.retryWithBackoff
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException
import java.time.Instant
import kotlin.random.Random

class BalancesTest {

    private fun expense(amount: Long, paidBy: String, vararg split: String) =
        Expense("", "x", amount, paidBy, split.toList(), paidBy, Instant.EPOCH)

    @Test
    fun `equal split with leftover cents still adds up`() {
        val net = Balances.net(listOf("a", "b", "c"), listOf(expense(10_00, "a", "a", "b", "c")))

        // 1000 cents / 3 = 334 + 333 + 333
        assertEquals(mapOf("a" to 666L, "b" to -333L, "c" to -333L), net)
        assertEquals(0L, net.values.sum())
    }

    @Test
    fun `settlements pay the biggest creditor first and clear every balance`() {
        val expenses = listOf(
            expense(90_000_00, "ana", "ana", "beto", "caro"),  // arriendo
            expense(30_000_00, "beto", "ana", "beto", "caro"), // mercado
        )
        val net = Balances.net(listOf("ana", "beto", "caro"), expenses)
        val settlements = Balances.settle(net)

        assertEquals(
            listOf(Settlement("caro", "ana", 40_000_00), Settlement("beto", "ana", 10_000_00)),
            settlements
        )
    }

    @Test
    fun `nobody owes anything when everyone paid their share`() {
        val net = Balances.net(listOf("a", "b"), listOf(expense(100, "a", "a", "b"), expense(100, "b", "a", "b")))
        assertTrue(Balances.settle(net).isEmpty())
    }
}

class ParsingTest {

    @Test
    fun `amounts written the colombian way`() {
        assertEquals(4_500_000L, parseAmountToCents("45.000"))
        assertEquals(4_500_000L, parseAmountToCents("$ 45000"))
        assertEquals(1_250L, parseAmountToCents("12,50"))
        assertEquals(1_250L, parseAmountToCents("12.5"))
        assertEquals(123_456_700L, parseAmountToCents("1.234.567"))
        assertEquals(null, parseAmountToCents("abc"))
    }

    @Test
    fun `invite codes skip look-alike characters`() {
        repeat(200) {
            val code = generateInviteCode(Random(it))
            assertEquals(6, code.length)
            assertTrue(code.none { c -> c in "01ILO" })
        }
    }
}

class UseCaseTest {

    @Test
    fun `retries transient errors with backoff and gives up on permanent ones`() = runTest {
        var calls = 0
        val result = retryWithBackoff(attempts = 3, isTransient = { it is IOException }) {
            calls++
            if (calls < 3) throw IOException("red") else "ok"
        }
        assertEquals("ok", result)
        assertEquals(3, calls)

        var permanentCalls = 0
        val failure = runCatching {
            retryWithBackoff(isTransient = { it is IOException }) {
                permanentCalls++
                throw SecurityException("sin permiso")
            }
        }
        assertTrue(failure.exceptionOrNull() is SecurityException)
        assertEquals(1, permanentCalls)
    }

    @Test
    fun `sign up validates before calling firebase and remembers the email`() = runTest {
        val auth = mockk<AuthRepository>()
        val session = mockk<SessionStore>(relaxed = true)
        coEvery { auth.signUp(any(), any(), any()) } returns Result.success(User("u1", "Ana", "ana@x.co"))
        val signUp = SignUpUseCase(auth, session)

        assertTrue(signUp("Ana", "ana@x.co", "corta1").isFailure)
        assertTrue(signUp("Ana", "ana@x.co", "sololetras").isFailure)
        coVerify(exactly = 0) { auth.signUp(any(), any(), any()) }

        assertTrue(signUp(" Ana ", " ANA@x.co ", "segura123").isSuccess)
        coVerify { auth.signUp("Ana", "ana@x.co", "segura123") }
        verify { session.lastEmail = "ana@x.co" }
    }

    @Test
    fun `expense amount is stored in cents and validated`() = runTest {
        val groups = mockk<GroupRepository>()
        val saved = slot<Expense>()
        coEvery { groups.addExpense("g1", capture(saved)) } returns Result.success(Unit)
        val add = AddExpenseUseCase(groups)

        assertTrue(add("g1", "Pizza", "0", "a", listOf("a"), "a").isFailure)
        assertTrue(add("g1", "Pizza", "52.000", "a", emptyList(), "a").isFailure)
        assertTrue(add("g1", "  Pizza ", "52.000", "a", listOf("a", "b"), "a").isSuccess)
        assertEquals(5_200_000L, saved.captured.amountCents)
        assertEquals("Pizza", saved.captured.description)
    }
}
