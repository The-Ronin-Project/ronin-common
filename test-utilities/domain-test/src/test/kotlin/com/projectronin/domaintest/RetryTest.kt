package com.projectronin.domaintest

import kotlinx.coroutines.runBlocking
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import java.time.Duration

class RetryTest {

    @Test
    fun `should retry N times`() {
        var tries = 0
        val result = retryDefault(10, Duration.ofMillis(10)) {
            tries += 1
            throw Exception("ERROR")
        }
        assertThat(result.isFailure).isTrue()
        assertThat(tries).isEqualTo(10)
    }

    @Test
    fun `should retry with validation and succeed`() {
        var tries = 0
        val result = retryDefault(10, Duration.ofMillis(10), { it < 10 }) {
            tries += 1
            tries
        }
        assertThat(result.isSuccess).isTrue()
        assertThat(tries).isEqualTo(10)
        assertThat(result.getOrThrow()).isEqualTo(10)
    }

    @Test
    fun `should handle assertions`() {
        var tries = 0
        val result = retryAssertion(10, Duration.ofMillis(10)) {
            tries += 1
            assertThat(tries).isEqualTo(10)
            tries
        }
        assertThat(result.isSuccess).isTrue()
        assertThat(tries).isEqualTo(10)
        assertThat(result.getOrThrow()).isEqualTo(10)
    }

    @Test
    fun `should work with the builder`() {
        var tries = 0
        val result = retry<Int> {
            maxAttempts(10)
                .waitDuration(Duration.ofMillis(10))
                .retryOnResult { it < 10 }
        }.executeCatchingWithType {
            tries += 1
            tries
        }
        assertThat(result.isSuccess).isTrue()
        assertThat(tries).isEqualTo(10)
        assertThat(result.getOrThrow()).isEqualTo(10)
    }

    @Test
    fun `should work with the builder with Any`() {
        runBlocking {
            var tries = 0
            val result = retry<Any> {
                maxAttempts(10)
                    .waitDuration(Duration.ofMillis(10))
            }.executeSuspendCatchingWithType {
                tries += 1
                if (tries < 10) {
                    throw IllegalStateException("!")
                }
                tries
            }
            assertThat(result.isSuccess).isTrue()
            assertThat(tries).isEqualTo(10)
            assertThat(result.getOrThrow()).isEqualTo(10)
        }
    }
}
