package com.voxcom.tikitopple.model

data class MoveResult(

    val success: Boolean,

    val board: List<Int>,

    val remainingCards: List<String> = emptyList(),

    val nextTurn: String = "",

    val message: String = ""

)