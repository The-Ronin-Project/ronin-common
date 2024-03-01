package com.projectronin.oci.auth

import com.oracle.bmc.Region
import com.oracle.bmc.auth.SimpleAuthenticationDetailsProvider
import com.projectronin.oci.OciProperties
import java.util.Base64

class OciAuth(private val ociProperties: OciProperties) {

    val provider: SimpleAuthenticationDetailsProvider = SimpleAuthenticationDetailsProvider.builder()
        .tenantId(ociProperties.tenant)
        .userId(ociProperties.user)
        .fingerprint(ociProperties.fingerprint)
        .privateKeySupplier { Base64.getDecoder().decode(ociProperties.privateKey).inputStream() }
        .region(Region.fromRegionCode(ociProperties.primaryRegion))
        .build()
}
