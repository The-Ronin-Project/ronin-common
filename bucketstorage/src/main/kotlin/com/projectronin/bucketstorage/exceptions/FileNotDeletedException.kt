package com.projectronin.bucketstorage.exceptions

class FileNotDeletedException(path: String) : RuntimeException("File was not deleted at the location $path.")
