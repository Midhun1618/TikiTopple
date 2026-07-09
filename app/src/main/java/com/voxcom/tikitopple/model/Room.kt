package com.voxcom.tikitopple.model

data class Room(

    val roomCode: String = "",

    val hostUid: String = "",

    val state: String = "WAITING",

    val initialized: Boolean = false,

    val createdAt: Long = 0L

)