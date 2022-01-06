package com.example.hyppotunes.core.utils

import java.io.FileInputStream
import java.io.IOException

fun FileInputStream.availableOrZero(): Int {
    return try {
        available()
    } catch (e: IOException) {
        e.printStackTrace()
        0
    }
}

fun FileInputStream.readByteArrayOrMinusOne(byteArray: ByteArray): Int {
    return try {
        read(byteArray)
    } catch (e: IOException) {
        e.printStackTrace()
        -1
    }
}