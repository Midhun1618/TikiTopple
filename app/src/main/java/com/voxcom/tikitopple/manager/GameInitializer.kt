package com.voxcom.tikitopple.manager

import com.google.firebase.database.FirebaseDatabase
import com.voxcom.tikitopple.model.GameData
import com.voxcom.tikitopple.model.GamePlayer
import com.voxcom.tikitopple.model.LobbyPlayer
import com.voxcom.tikitopple.model.RoomState

class GameInitializer(

    private val roomCode: String,

    private val callback: GameInitializationCallback

) {

    private val database = FirebaseDatabase.getInstance().reference

    fun initializeGame() {

        android.util.Log.d("GAME_INIT", "initializeGame()")

        callback.onStatusChanged("Creating Match")

        fetchPlayers()

    }

    // ---------------------------------------------------------

    private fun fetchPlayers() {

        database.child("rooms")
            .child(roomCode)
            .child("players")
            .get()
            .addOnSuccessListener { snapshot ->

                android.util.Log.d(
                    "GAME_INIT",
                    "Players fetched = ${snapshot.childrenCount}"
                )

                val players = mutableListOf<LobbyPlayer>()

                snapshot.children.forEach {

                    val player =
                        it.getValue(LobbyPlayer::class.java)

                    if(player!=null)
                        players.add(player)

                }

                android.util.Log.d("GAME_INIT", "Assign Secret Cards")
                assignSecretCards(players)


            }
            .addOnFailureListener {

                callback.onError("Unable to fetch players.")

            }

    }

    // ---------------------------------------------------------

    private fun assignSecretCards(

        lobbyPlayers: List<LobbyPlayer>

    ) {

        callback.onStatusChanged(
            "Assigning Secret Cards..."
        )

        val assignments =
            SecretCardManager.assign(
                lobbyPlayers.map { it.uid }
            )

        createBoard(
            lobbyPlayers,
            assignments
        )

    }

    // ---------------------------------------------------------

    private fun createBoard(

        lobbyPlayers: List<LobbyPlayer>,

        assignments: Map<String,String>

    ) {

        android.util.Log.d("GAME_INIT", "Create Board")

        callback.onStatusChanged(
            "Creating Board..."
        )

        val board =
            (1..9).shuffled()

        generateTurnOrder(
            lobbyPlayers,
            assignments,
            board
        )

    }

    // ---------------------------------------------------------

    private fun generateTurnOrder(

        lobbyPlayers: List<LobbyPlayer>,

        assignments: Map<String,String>,

        board: List<Int>

    ) {

        android.util.Log.d("GAME_INIT", "Generate Turn Order")

        callback.onStatusChanged(
            "Generating Turn Order..."
        )

        val order =
            TurnManager.generate(
                lobbyPlayers.map { it.uid }
            )

        dealCards(
            lobbyPlayers,
            assignments,
            board,
            order
        )

    }

    // ---------------------------------------------------------

    private fun dealCards(

        lobbyPlayers: List<LobbyPlayer>,

        assignments: Map<String,String>,

        board: List<Int>,

        turnOrder: List<String>

    ) {

        android.util.Log.d("GAME_INIT", "Deal Cards")

        callback.onStatusChanged(
            "Dealing Cards..."
        )

        val gamePlayers =
            mutableMapOf<String,GamePlayer>()

        lobbyPlayers.forEach {

            gamePlayers[it.uid] = GamePlayer(

                uid = it.uid,

                score = 0,

                secretCard = assignments[it.uid]!!,

                cards = ActionCardManager.deal()

            )

        }

        saveGame(
            board,
            turnOrder,
            gamePlayers
        )

    }

    // ---------------------------------------------------------

    private fun saveGame(

        board: List<Int>,

        turnOrder: List<String>,

        players: Map<String,GamePlayer>

    ) {

        android.util.Log.d("GAME_INIT", "saveGame()")

        callback.onStatusChanged(
            "Saving Game..."
        )

        val game = GameData(

            board = board,

            round = 1,

            currentTurn = turnOrder.first(),

            turnOrder = turnOrder,

            players = players

        )

        val updates = hashMapOf<String,Any>(

            "rooms/$roomCode/game" to game,

            "rooms/$roomCode/state" to RoomState.PLAYING.name,

            "rooms/$roomCode/initialized" to true

        )

        database.updateChildren(updates)
            .addOnSuccessListener {

                callback.onCompleted()

            }
            .addOnFailureListener {

                callback.onError(
                    "Unable to start game."
                )

            }

    }

}