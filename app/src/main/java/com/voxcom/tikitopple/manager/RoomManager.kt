package com.voxcom.tikitopple.manager

import android.content.Context
import android.content.SharedPreferences
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.voxcom.tikitopple.model.LobbyPlayer
import com.voxcom.tikitopple.model.Room
import com.voxcom.tikitopple.model.RoomState
import com.voxcom.tikitopple.utils.RoomCodeGenerator
import com.google.firebase.database.DataSnapshot
import com.google.firebase.database.DatabaseError
import com.google.firebase.database.ValueEventListener

class RoomManager(
    context: Context,
    private val callback: RoomCallback
) {

    private val auth = FirebaseAuth.getInstance()

    private var currentRoomCode: String? = null
    private val database = FirebaseDatabase.getInstance().reference

    private val prefs: SharedPreferences =
        context.getSharedPreferences("tiki_topple", Context.MODE_PRIVATE)

    private val uid = auth.currentUser!!.uid

    companion object {
        private const val CURRENT_ROOM = "current_room"
    }

    // ==========================================================
    // HOST
    // ==========================================================

    fun createRoom() {
        generateUniqueRoomCode()
    }

    private fun generateUniqueRoomCode() {

        val roomCode = RoomCodeGenerator.generate()

        database.child("rooms")
            .child(roomCode)
            .get()
            .addOnSuccessListener { snapshot ->

                if (snapshot.exists()) {
                    generateUniqueRoomCode()
                } else {
                    createRoomInDatabase(roomCode)
                }

            }
            .addOnFailureListener {
                callback.onError("Unable to create room.")
            }

    }

    private fun saveCurrentRoom(roomCode: String) {

        currentRoomCode = roomCode

        prefs.edit()
            .putString(CURRENT_ROOM, roomCode)
            .apply()

    }

    fun getHostedRoom(): String? {
        return prefs.getString(CURRENT_ROOM, null)
    }

    private fun createRoomInDatabase(roomCode: String) {

        val room = Room(

            roomCode = roomCode,

            hostUid = uid,

            state = RoomState.WAITING.name,

            createdAt = System.currentTimeMillis()

        )

        database.child("rooms")
            .child(roomCode)
            .setValue(room)
            .addOnSuccessListener {

                joinSelf(roomCode)

            }
            .addOnFailureListener {

                callback.onError("Failed to create room.")

            }

    }

    private fun joinSelf(roomCode: String) {

        database.child("players")
            .child(uid)
            .get()
            .addOnSuccessListener { snapshot ->

                val name =
                    snapshot.child("name")
                        .getValue(String::class.java)
                        ?: "Player"

                val player = LobbyPlayer(

                    uid = uid,

                    name = name,

                    ready = false,

                    host = true,

                    joinedAt = System.currentTimeMillis()

                )

                database.child("rooms")
                    .child(roomCode)
                    .child("players")
                    .child(uid)
                    .setValue(player)
                    .addOnSuccessListener {

                        saveCurrentRoom(roomCode)

                        callback.onRoomCreated(roomCode)

                    }

            }

    }

    // ==========================================================
    // JOIN
    // ==========================================================

    fun joinRoom(roomCode: String) {
        validateRoom(roomCode)
    }


    private fun validateRoom(roomCode: String) {

        database.child("rooms")
            .child(roomCode)
            .get()
            .addOnSuccessListener { snapshot ->

                if (!snapshot.exists()) {
                    callback.onError("Room not found.")
                    return@addOnSuccessListener
                }

                val room =
                    snapshot.getValue(Room::class.java)
                        ?: run {
                            callback.onError("Invalid room.")
                            return@addOnSuccessListener
                        }

                if (room.state != "WAITING") {
                    callback.onError("Game already started.")
                    return@addOnSuccessListener
                }

                val playersSnapshot = snapshot.child("players")

                if (playersSnapshot.childrenCount >= 4) {
                    callback.onError("Room is full.")
                    return@addOnSuccessListener
                }

                if (playersSnapshot.hasChild(uid)) {
                    callback.onRoomJoined(roomCode)
                    return@addOnSuccessListener
                }

                database.child("players")
                    .child(uid)
                    .get()
                    .addOnSuccessListener { playerSnapshot ->

                        val name = playerSnapshot.child("name")
                            .getValue(String::class.java)
                            ?: "Player"

                        val player = LobbyPlayer(
                            uid = uid,
                            name = name,
                            ready = false,
                            host = false,
                            joinedAt = System.currentTimeMillis()
                        )

                        database.child("rooms")
                            .child(roomCode)
                            .child("players")
                            .child(uid)
                            .setValue(player)
                            .addOnSuccessListener {

                                callback.onRoomJoined(roomCode)

                            }
                            .addOnFailureListener {

                                callback.onError("Failed to join room.")

                            }

                    }

            }
            .addOnFailureListener {

                callback.onError("Unable to connect to Firebase.")

            }

    }

    // ==========================================================
    // LOBBY
    // ==========================================================

    fun setReady(isReady: Boolean) {

        val roomCode = currentRoomCode ?: return

        database.child("rooms")
            .child(roomCode)
            .child("players")
            .child(uid)
            .child("ready")
            .setValue(isReady)
            .addOnFailureListener {

                callback.onError("Unable to update ready state.")

            }

    }

    fun listenLobby(roomCode: String) {

        database.child("rooms")
            .child(roomCode)
            .child("players")
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    val players = mutableListOf<LobbyPlayer>()

                    snapshot.children.forEach { playerSnapshot ->

                        val player = playerSnapshot.getValue(LobbyPlayer::class.java)

                        if (player != null) {
                            players.add(player)
                        }

                    }

                    players.sortBy { it.joinedAt }

                    callback.onLobbyUpdated(players)

                    val totalPlayers = players.size

                    val readyPlayers = players.count { it.ready }

                    val allReady =
                        totalPlayers >= 2 &&
                                readyPlayers == totalPlayers

                    callback.onReadyStateChanged(
                        readyPlayers,
                        totalPlayers,
                        allReady
                    )

                }

                override fun onCancelled(error: DatabaseError) {

                    callback.onError(error.message)

                }

            })

    }

    fun leaveRoom(roomCode: String) {

    }

    private fun deleteRoom(roomCode: String) {

    }

    // ==========================================================
    // GAME
    // ==========================================================

    fun beginGame() {

        val roomCode = currentRoomCode ?: return

        database.child("rooms")
            .child(roomCode)
            .child("hostUid")
            .get()
            .addOnSuccessListener { snapshot ->

                val hostUid = snapshot.getValue(String::class.java)

                if (hostUid != uid) {
                    return@addOnSuccessListener
                }

                // TODO:
                // assign secret cards
                // initialize board
                // create turn order
                // state = PLAYING
            }

    }

}