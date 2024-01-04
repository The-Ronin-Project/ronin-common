package com.projectronin.common.exceptions

abstract class RetryableException(cause: Throwable) : RuntimeException(cause)
