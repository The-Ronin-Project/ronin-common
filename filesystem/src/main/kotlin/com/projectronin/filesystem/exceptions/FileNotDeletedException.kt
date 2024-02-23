package com.projectronin.filesystem.exceptions

class FileNotDeletedException(path: String) : RuntimeException("File was not deleted at the location $path.")
