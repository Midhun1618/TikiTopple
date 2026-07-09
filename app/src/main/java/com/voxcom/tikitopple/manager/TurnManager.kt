package com.voxcom.tikitopple.manager

object TurnManager {

    fun generate(players: List<String>): List<String> {

        return players.shuffled()

    }

    fun nextPlayer(
        turnOrder: List<String>,
        currentPlayer: String
    ): String {

        val index = turnOrder.indexOf(currentPlayer)

        return turnOrder[(index + 1) % turnOrder.size]

    }

    fun previousPlayer(
        turnOrder: List<String>,
        currentPlayer: String
    ): String {

        val index = turnOrder.indexOf(currentPlayer)

        return if(index==0)
            turnOrder.last()
        else
            turnOrder[index-1]

    }

}