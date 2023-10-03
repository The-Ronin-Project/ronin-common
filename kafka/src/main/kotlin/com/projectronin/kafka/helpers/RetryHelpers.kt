package com.projectronin.kafka.helpers

import mu.KotlinLogging

private val logger = KotlinLogging.logger { }

fun <R> withRetry(fn: () -> R): R {
    // TODO: use resillience4j instead of this janky mess

    var failedAttempts = 0
    while (true) {
        try {
            return fn()
        } catch (ex: Exception) {
            failedAttempts += 1

            val sleepSeconds = when (failedAttempts) {
                1 -> 2
                2 -> 30
                3 -> 180
                4 -> 600
                else -> 3600
            }

            logger.warn(ex) { "Processing record has failed $failedAttempts times -- will continue retrying" }

            if (failedAttempts == 5) {
                // Once we're far enough into the retrying ( it's been over 10 minutes of retries at this point),
                // spit something out, so we have an easy thing to search for in datadog to know we're in a stuck state
                logger.error(ex) { "Processing record has failed 5 times and processing is likely stuck" }
            }

            // TODO: can kafka streams even handle processing spinning for like 10 minutes? or do we need to bail with
            //  an exception at some point and let StreamsUncaughtExceptionHandler replace the thread as our way of
            //  retrying forever?
            Thread.sleep(sleepSeconds * 1000L)
        }
    }
}
