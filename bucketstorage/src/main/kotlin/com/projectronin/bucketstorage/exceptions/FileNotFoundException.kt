package com.projectronin.bucketstorage.exceptions

class FileNotFoundException(path: String) : RuntimeException("No file was found at the location $path.")
