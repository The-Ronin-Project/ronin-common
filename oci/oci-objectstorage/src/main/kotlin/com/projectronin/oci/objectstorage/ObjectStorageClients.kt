package com.projectronin.oci.objectstorage

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorage
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.projectronin.oci.OciProperties

class ObjectStorageClients(
    ociProperties: OciProperties,
    private val authProvider: AbstractAuthenticationDetailsProvider
) {
    val primary: RoninOciClient = createClient(ociProperties.primaryRegion)
    val secondary: RoninOciClient? = ociProperties.secondaryRegion?.let { createClient(it) }

    private fun createClient(region: String): RoninOciClient {
        return RoninOciClient(
            region,
            ObjectStorageClient.builder()
                .region(region)
                .build(authProvider)
        )
    }
}

class RoninOciClient(val region: String, private val client: ObjectStorage) :
    ObjectStorage by client
