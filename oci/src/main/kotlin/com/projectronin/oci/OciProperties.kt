package com.projectronin.oci

data class OciProperties(
    val tenant: String,
    val compartment: String,
    val user: String,
    val fingerprint: String,
    val privateKey: String,
    val primaryRegion: String,
    val secondaryRegion: String? = null,
    val vault: String,
    val masterKey: String
)
