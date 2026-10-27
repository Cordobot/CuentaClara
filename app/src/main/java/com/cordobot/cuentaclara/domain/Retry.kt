package com.cordobot.cuentaclara.domain

import kotlinx.coroutines.delay
import kotlin.coroutines.cancellation.CancellationException

/**
 * Runs [block] up to [attempts] times, waiting [initialDelayMs] × [factor]^n between tries, but
 * only for errors [isTransient] accepts (a network blip, not a permission error).
 */
suspend fun <T> retryWithBackoff(
    attempts: Int = 3,
    initialDelayMs: Long = 500,
    factor: Double = 2.0,
    isTransient: (Throwable) -> Boolean,
    block: suspend () -> T
): T {
    var wait = initialDelayMs
    repeat(attempts - 1) {
        try {
            return block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Throwable) {
            if (!isTransient(e)) throw e
        }
        delay(wait)
        wait = (wait * factor).toLong()
    }
    return block()
}
