package com.example.hyppotunes.core.utils

import java.io.File
import java.io.FileOutputStream
import java.io.IOException

class FlowFileWriter private constructor(
    private val outputFile: File,
    private val tempFile: File,
    private val fileOutputStream: FileOutputStream
) {

    var isOpen = true

    class Builder {
        fun build(outputFile: File): FlowFileWriter? {
            if (outputFile.isDirectory) return null
            val tempFilePath = if (outputFile.parent != "null") {
                "${outputFile.parent}/${outputFile.nameWithoutExtension}.tmp"
            } else {
                "${outputFile.nameWithoutExtension}.tmp"
            }
            val tempFile = File(tempFilePath)
            outputFile.delete()
            tempFile.delete()
            val fileOutputStream = try {
                FileOutputStream(tempFile)
            } catch (e: IOException) {
                e.printStackTrace()
                return null
            }
            return FlowFileWriter(outputFile, tempFile, fileOutputStream)
        }
    }

    fun write(byteArray: ByteArray): Boolean {
        if (!isOpen) return false
        return try {
            fileOutputStream.write(byteArray)
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
    }

    fun abort(): Boolean {
        return close() && tempFile.delete() && outputFile.delete()
    }

    fun finalize(): Boolean {
        return close() && tempFile.renameTo(outputFile)
    }

    private fun close(): Boolean {
        if (!isOpen) return true
        val closedSuccessfully = try {
            fileOutputStream.close()
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
        if (closedSuccessfully) isOpen = false
        return closedSuccessfully
    }
}



