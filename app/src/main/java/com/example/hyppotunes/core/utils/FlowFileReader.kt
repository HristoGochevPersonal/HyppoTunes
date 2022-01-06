package com.example.hyppotunes.core.utils

import java.io.File
import java.io.FileInputStream
import java.io.FileNotFoundException
import java.io.IOException

class FlowFileReader private constructor(
    private val inputFile: File,
    private val fileInputStream: FileInputStream
) {

    var isOpen = true

    class Builder {
        fun build(inputFile: File): FlowFileReader? {
            if (!inputFile.exists() || inputFile.isDirectory) return null
            val fileInputStream = try {
                FileInputStream(inputFile)
            } catch (e: FileNotFoundException) {
                e.printStackTrace()
                return null
            }
            return FlowFileReader(inputFile, fileInputStream)
        }
    }

    fun readAtOnce(): ByteArray? {
        if (!isOpen) return null
        val availableBytes = fileInputStream.availableOrZero()
        if (availableBytes == 0) {
            close()
            return null
        }
        val output = ByteArray(availableBytes)
        val readSize = fileInputStream.readByteArrayOrMinusOne(output)
        val readSuccessfully = readSize != -1 && readSize == availableBytes
        close()
        return if (readSuccessfully) output
        else null
    }

    fun startReading(onBytesRead: (byteArray: ByteArray) -> Boolean): Boolean {
        if (!isOpen) return false
        val readSuccessfully: Boolean
        while (true) {
            var readBytes: Long = 0
            val availableBytes = fileInputStream.availableOrZero()
            if (availableBytes == 0) {
                readSuccessfully = readBytes == inputFile.length()
                break
            }
            val bytesToRead = if (availableBytes > 16384) 16384 else availableBytes
            val output = ByteArray(bytesToRead)
            val readSize = fileInputStream.readByteArrayOrMinusOne(output)
            if (readSize == -1 || readSize != bytesToRead) {
                readSuccessfully = false
                break
            } else {
                readBytes += readSize
                if (!onBytesRead(output)) {
                    readSuccessfully = false
                    break
                }
            }
        }
        close()
        return readSuccessfully
    }

    private fun close(): Boolean {
        if (!isOpen) return true
        val closedSuccessfully = try {
            fileInputStream.close()
            true
        } catch (e: IOException) {
            e.printStackTrace()
            false
        }
        if (closedSuccessfully) isOpen = false
        return closedSuccessfully
    }
}