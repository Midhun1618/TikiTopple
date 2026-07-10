package com.voxcom.tikitopple.manager

import com.google.firebase.database.FirebaseDatabase
import com.voxcom.tikitopple.model.GameData
import com.voxcom.tikitopple.model.GamePhase
import com.voxcom.tikitopple.model.GamePlayer
import com.voxcom.tikitopple.model.LastMove

class RoundManager(
    private val roomCode: String
) {

    private val database =
        FirebaseDatabase.getInstance().reference

    fun startNextRound(game: GameData) {

        if(game.gamePhase != GamePhase.ROUND_RESULTS)
            return

        // GAME OVER

        if (game.round >= 3) {

            database.child("rooms")
                .child(roomCode)
                .child("game")
                .child("gamePhase")
                .setValue(GamePhase.GAME_OVER)

            return
        }

        // New board

        val board =
            (1..9).shuffled()

        // New turn order

        val order =
            TurnManager.generate(
                game.players.keys.toList()
            )

        // New secret cards

        val assignments =
            SecretCardManager.assign(
                game.players.keys.toList()
            )

        // New players

        val newPlayers =
            mutableMapOf<String, GamePlayer>()

        game.players.values.forEach { player ->

            newPlayers[player.uid] = GamePlayer(

                uid = player.uid,

                name = player.name,

                score = player.score,

                secretCard = assignments[player.uid]!!,

                cards = ActionCardManager.deal()

            )

        }

        // Upload

        val updates =
            hashMapOf<String, Any>()

        updates["rooms/$roomCode/game"] =

            GameData(

                board = board,

                round = game.round + 1,

                currentTurn = order.first(),

                turnOrder = order,

                players = newPlayers,

                lastMove = LastMove(),

                gamePhase = GamePhase.MOVE,

                roundScores = emptyMap()

            )

        database.updateChildren(updates)

    }

}