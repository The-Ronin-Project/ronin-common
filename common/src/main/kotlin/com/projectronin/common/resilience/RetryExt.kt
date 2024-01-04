package com.projectronin.common.resilience

import io.github.resilience4j.kotlin.retry.executeFunction
import io.github.resilience4j.kotlin.retry.executeSuspendFunction

fun <T> io.github.resilience4j.retry.Retry.executeCatching(block: () -> T): Result<T> =
    runCatching { this.executeFunction { block() } }

suspend fun <T> io.github.resilience4j.retry.Retry.executeSuspendCatching(block: suspend () -> T): Result<T> =
    runCatching { this.executeSuspendFunction { block() } }
