package com.projectronin.common.exceptions

class ConnectionException(cause: Throwable) : RetryableException(cause)
