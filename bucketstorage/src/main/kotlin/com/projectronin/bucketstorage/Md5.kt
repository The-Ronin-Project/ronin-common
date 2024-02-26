package com.projectronin.bucketstorage

import java.security.MessageDigest
import java.util.Base64

@JvmInline
value class Md5(val value: String) {
    override fun toString() = value
}

fun ByteArray.md5(): Md5 {
    val md = MessageDigest.getInstance("MD5")
    return Md5(Base64.getEncoder().encodeToString(md.digest(this)))
}
