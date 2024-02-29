package com.projectronin.oci.objectstorage

import com.oracle.bmc.auth.AbstractAuthenticationDetailsProvider
import com.oracle.bmc.objectstorage.ObjectStorage
import com.oracle.bmc.objectstorage.ObjectStorageClient
import com.oracle.bmc.objectstorage.requests.GetNamespaceRequest

class OciObjectStorageClient private constructor(val region: String, private val client: ObjectStorage) :
    ObjectStorage by client {
    private var namespace: String? = null

    constructor(region: String, authProvider: AbstractAuthenticationDetailsProvider) : this(
        region,
        ObjectStorageClient.builder()
            .region(region)
            .build(authProvider)
    )

    fun getNamespace(): String {
        if (namespace == null) {
            namespace = getNamespace(GetNamespaceRequest.builder().build()).value
        }

        return namespace!!
    }
}
