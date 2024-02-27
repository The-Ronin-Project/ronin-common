package com.projectronin.bucketstorage.exceptions

class FileNotDeletedException : RuntimeException {
    constructor(path: String, cause: Exception?) : super(message(path), cause)
    constructor(path: String) : super(message(path))

    companion object {
        fun message(path: String): String {
            return "File was not deleted at the location $path."
        }
    }
}
