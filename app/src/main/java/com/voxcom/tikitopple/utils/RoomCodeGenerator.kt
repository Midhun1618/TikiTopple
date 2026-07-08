package com.voxcom.tikitopple.utils

import kotlin.random.Random

object RoomCodeGenerator {

    private const val CHARACTERS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    fun generate(length: Int = 5): String {
        return buildString {
            repeat(length) {
                append(CHARACTERS[Random.nextInt(CHARACTERS.length)])
            }
        }
    }
}