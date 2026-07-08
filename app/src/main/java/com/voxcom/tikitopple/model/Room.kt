package com.voxcom.tikitopple.model

data class Room(

    val roomCode: String = "",

    val hostUid: String = "",

    val state: String = "WAITING",

    val createdAt: Long = 0L

)