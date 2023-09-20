package com.projectronin.auth.token

class RoninName(
    val fullText: String,
    val familyName: String?,
    val givenName: List<String>,
    val prefix: List<String>,
    val suffix: List<String>
)
