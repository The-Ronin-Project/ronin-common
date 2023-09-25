package com.projectronin.auth.m2m

fun interface TokenListener {
    fun tokenChanged(newToken: TokenResponse)
}
