package org.jason.server.utils

import ch.qos.logback.core.PropertyDefinerBase
import java.io.File

class LogHomeProperty : PropertyDefinerBase() {
    override fun getPropertyValue(): String {
        return getDirectory().absolutePath
    }

    private fun getDirectory(): File {
        val directory = File(FileUtil.getDataDirectory(), "Logger")
        if (!directory.exists()) {
            directory.mkdirs()
        }
        return directory
    }
}