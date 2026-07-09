package com.voxcom.tikitopple.manager

import com.voxcom.tikitopple.model.LobbyPlayer

interface RoomCallback {

    /**
     * Called when the room is successfully created.
     */
    fun onRoomCreated(roomCode: String)

    /**
     * Called when a player successfully joins a room.
     */
    fun onRoomJoined(roomCode: String)

    /**
     * Called whenever the lobby player list changes.
     */
    fun onLobbyUpdated(players: List<LobbyPlayer>)

    /**
     * Called when the host starts the game.
     */
    fun onGameStarted(roomCode: String)

    fun onReadyStateChanged(
        readyPlayers: Int,
        totalPlayers: Int,
        allReady: Boolean
    )
    fun onHostShouldInitializeGame()
    /**
     * Called whenever an operation fails.
     */
    fun onError(message: String)
}