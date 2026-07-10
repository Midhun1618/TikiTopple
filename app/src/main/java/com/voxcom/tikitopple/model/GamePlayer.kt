package com.voxcom.tikitopple.model

data class GamePlayer(

    val uid: String = "",

    val name: String = "",

    val score: Int = 0,

    val secretCard: String = "",

    val cards: List<String> = emptyList()

)