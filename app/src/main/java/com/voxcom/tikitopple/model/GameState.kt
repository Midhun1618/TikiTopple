package com.voxcom.tikitopple.model

data class GameState(

    val round: Int = 1,

    val currentTurn: String = "",

    val turnOrder: List<String> = emptyList(),

    val board: List<Int> = emptyList(),

    val winnerUid: String = ""

)