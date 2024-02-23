package com.projectronin.oci

interface OciProperties {
    val tenant: String
    val user: String
    val fingerprint: String
    val privateKey: String
    val primaryRegion: String
    val secondaryRegion: String?
}
