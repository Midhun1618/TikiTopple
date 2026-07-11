package com.voxcom.tikitopple.model

data class LobbyPlayer(

    val uid: String = "",

    val name: String = "",

    val ready: Boolean = false,

    val host: Boolean = false,

    val joinedAt: Long = 0L,

    val avatarIndex: Int = 0

)