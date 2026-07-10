package com.voxcom.tikitopple.model

data class GameData(

    val board: List<Int> = emptyList(),

    val round: Int = 1,

    val currentTurn: String = "",

    val turnOrder: List<String> = emptyList(),

    val players: Map<String, GamePlayer> = emptyMap(),

    val lastMove: LastMove = LastMove(),

    val gamePhase: String = GamePhase.MOVE,

    val roundScores: Map<String, Int> = emptyMap()

)