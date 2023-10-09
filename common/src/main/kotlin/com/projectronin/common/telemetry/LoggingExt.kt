package com.projectronin.common.telemetry

private fun String.withMetadata(
    metadata: List<Pair<String, Any>> = emptyList()
) = buildString {
    append(this@withMetadata)
    val metadataString = metadata.joinToString(", ", "[", "]") { (key, value) ->
        "$key: $value"
    }
    if (metadataString.isNotBlank() && metadataString != "[]") {
        if (isNotEmpty()) {
            append(" ")
        }
        append(metadataString)
    }
}

private fun String.withMetadata(
    vararg metadata: Pair<String, Any>
) = withMetadata(metadata.toList())

private fun String.withMetadata(
    metadata: Map<String, Any>
) = withMetadata(metadata.toList())
