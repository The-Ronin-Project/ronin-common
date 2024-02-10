package com.projectronin.domaintest

import com.projectronin.common.resilience.executeCatching
import io.github.resilience4j.kotlin.retry.executeFunction
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import java.time.Duration
import java.util.UUID

/**
 * Used below.
 */
interface TypedRetry<T> : Retry {
    fun executeCatchingWithType(block: () -> T): Result<T> =
        runCatching { this.executeFunction { block() } }

    suspend fun executeSuspendCatchingWithType(block: suspend () -> T): Result<T> =
        runCatching { this.executeSuspendFunction { block() } }
}

/**
 * Creates a typed retry where the builder and the execution block share the same type.
 *
 * ```
 * val result = retry<Int> {
 *     maxAttempts(10)
 *         .waitDuration(Duration.ofMillis(10))
 *         .retryOnResult { it != 200 }
 * }.executeCatchingWithType {                        // or coExecuteCatchingWithType
 *     do something that returns an int
 * }
 * ```
 */
fun <T> retry(block: RetryConfig.Builder<T>.() -> RetryConfig.Builder<T>): TypedRetry<T> {
    val underlying = Retry.of("test-${UUID.randomUUID()}", block(RetryConfig.custom()).build())
    return object : Retry by underlying, TypedRetry<T> {}
}

/**
 * Creates a simple retry for max attempts pausing for duration between each one.  You can pass a result checker
 * if you want, and have to pass the block to execute.  E.g. the following will retry until the result (an Int) is >= 10.
 *
 * ```
 * val result = retryDefault(10, Duration.ofMillis(10), { it < 10 }) {
 *     // do something that returns an int
 * }
 * ```
 */
fun <T> retryDefault(attempts: Int, duration: Duration, resultChecker: ((T) -> Boolean)? = null, block: () -> T): Result<T> {
    return Retry.of(
        "test-${UUID.randomUUID()}",
        RetryConfig.custom<T>()
            .maxAttempts(attempts)
            .waitDuration(duration)
            .run { resultChecker?.let { retryOnResult(it) } ?: this }
            .build()
    ).executeCatching(block)
}

/**
 * Similar to [retryDefault], retries up to attempts, waiting duration.  But it is intended for there to be an assertion
 * that fails inside the block.  This exists because [Retry] passes [Error] instances out without retrying, or at least [AssertionError].  So this
 * just fixes that, so you can do:
 *
 * ```
 * val result = retryAssertion(10, Duration.ofMillis(10)) {
 *     // do something
 *     assertThat(something).isEqualTo(something else)
 *     // return something
 * }
 * ```
 */
fun <T> retryAssertion(attempts: Int, duration: Duration, block: () -> T): Result<T> {
    return Retry.of(
        "test-${UUID.randomUUID()}",
        RetryConfig.custom<T>()
            .maxAttempts(attempts)
            .waitDuration(duration)
            .build()
    ).executeCatching {
        try {
            block()
        } catch (e: AssertionError) {
            throw RuntimeException("Converting AssertionError to RuntimeException because resilience doesn't like AssertionError", e)
        }
    }
}
