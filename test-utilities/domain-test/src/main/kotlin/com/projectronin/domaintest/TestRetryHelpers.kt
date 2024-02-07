package com.projectronin.domaintest

import com.projectronin.common.resilience.executeCatching
import io.github.resilience4j.kotlin.retry.executeFunction
import io.github.resilience4j.kotlin.retry.executeSuspendFunction
import io.github.resilience4j.retry.Retry
import io.github.resilience4j.retry.RetryConfig
import java.time.Duration
import java.util.UUID

interface TypedRetry<T> : Retry {
    fun executeCatchingWithType(block: () -> T): Result<T> =
        runCatching { this.executeFunction { block() } }

    suspend fun executeSuspendCatchingWithType(block: suspend () -> T): Result<T> =
        runCatching { this.executeSuspendFunction { block() } }
}

fun <T> retry(block: RetryConfig.Builder<T>.() -> RetryConfig.Builder<T>): TypedRetry<T> {
    val underlying = Retry.of("test-${UUID.randomUUID()}", block(RetryConfig.custom()).build())
    return object : Retry by underlying, TypedRetry<T> {}
}

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
