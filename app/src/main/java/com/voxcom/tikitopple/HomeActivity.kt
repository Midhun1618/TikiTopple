package com.voxcom.tikitopple

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.media.MediaPlayer
import android.os.Bundle
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.*
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.voxcom.tikitopple.adapter.LobbyAdapter
import com.voxcom.tikitopple.manager.GameInitializationCallback
import com.voxcom.tikitopple.manager.GameInitializer
import com.voxcom.tikitopple.manager.RoomCallback
import com.voxcom.tikitopple.manager.RoomManager
import com.voxcom.tikitopple.model.LobbyPlayer

class HomeActivity : AppCompatActivity(), RoomCallback {

    private lateinit var usernameTv: TextView

    private lateinit var hostBtn: Button
    private lateinit var joinBtn: Button

    private var mediaPlayer: MediaPlayer? = null

    private lateinit var hostLL: LinearLayout
    private lateinit var joinLL: LinearLayout

    private lateinit var roomCodeTv: TextView
    private lateinit var roomCodeEt: EditText

    private lateinit var copyBtn: Button
    private lateinit var searchBtn: Button

    private lateinit var lobbyList: LinearLayout
    private lateinit var playersList: ListView
    private lateinit var readyBtn: Button

    private lateinit var adapter: LobbyAdapter

    private lateinit var loadingOverlay: ConstraintLayout
    private lateinit var loadingStatusTv: TextView

    private lateinit var roomManager: RoomManager

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference

    private lateinit var uid: String
    private var ready = false

    private lateinit var leftLeaf : ImageView
    private lateinit var rightLeaf : ImageView


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_home)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(
                bars.left,
                bars.top,
                bars.right,
                bars.bottom
            )
            insets
        }

        initializeViews()

        roomManager = RoomManager(this, this)

        uid = auth.currentUser!!.uid

        loadPlayer()

        initializeButtons()


        val animSwingR = AnimationUtils.loadAnimation(this, R.anim.swing_right)
        val animSwingL = AnimationUtils.loadAnimation(this, R.anim.swing_left)

        leftLeaf.startAnimation(animSwingR)
        rightLeaf.startAnimation(animSwingL)

        mediaPlayer = MediaPlayer.create(this, R.raw.home_bgm)
        mediaPlayer?.isLooping = true   // Loop forever
        mediaPlayer?.start()

    }

    private fun initializeViews() {

        usernameTv = findViewById(R.id.usernameTv)

        hostBtn = findViewById(R.id.hostBtn)
        joinBtn = findViewById(R.id.joinBtn)

        hostLL = findViewById(R.id.hostLL)
        joinLL = findViewById(R.id.joinLL)

        roomCodeTv = findViewById(R.id.roomcodeHTv)
        roomCodeEt = findViewById(R.id.roomcodeEt)

        copyBtn = findViewById(R.id.copyBtn)
        searchBtn = findViewById(R.id.searchBtn)

        searchBtn = findViewById(R.id.searchBtn)

        lobbyList = findViewById(R.id.lobbyList)
        playersList = findViewById(R.id.playersRoomList)

        readyBtn = findViewById(R.id.readyBtn)

        loadingOverlay = findViewById(R.id.loadingOverlay)
        loadingStatusTv = findViewById(R.id.loadingStatusTv)

        leftLeaf = findViewById(R.id.leaf_left)
        rightLeaf = findViewById(R.id.leaf_right)

        adapter = LobbyAdapter(
            this,
            mutableListOf()
        )

        playersList.adapter = adapter

    }

    private fun initializeButtons() {

        hostBtn.setOnClickListener {

            roomManager.createRoom()
            hostLL.visibility = View.VISIBLE
            joinLL.visibility = View.GONE
            lobbyList.visibility = View.VISIBLE

        }
        readyBtn.setOnClickListener {

            ready = !ready

            roomManager.setReady(ready)

            readyBtn.text =
                if(ready)
                    "Not Ready"
                else
                    "Ready"

        }

        joinBtn.setOnClickListener {

            lobbyList.visibility = View.VISIBLE
            hostLL.visibility = View.GONE
            joinLL.visibility = View.VISIBLE

        }

        copyBtn.setOnClickListener {

            val clipboard =
                getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager

            clipboard.setPrimaryClip(

                ClipData.newPlainText(
                    "Room Code",
                    roomCodeTv.text.toString()
                )

            )

            Toast.makeText(
                this,
                "Room Code Copied",
                Toast.LENGTH_SHORT
            ).show()

        }

        searchBtn.setOnClickListener {

            val code =
                roomCodeEt.text.toString()
                    .trim()
                    .uppercase()

            if (code.isNotEmpty()) {

                roomManager.joinRoom(code)

            }

        }

    }

    private fun loadPlayer() {

        database.child("players")
            .child(uid)
            .child("name")
            .get()
            .addOnSuccessListener {

                usernameTv.text =
                    it.getValue(String::class.java)

            }

    }

    // ==========================================================
    // CALLBACKS
    // ==========================================================

    override fun onRoomCreated(roomCode: String) {

        hostLL.visibility = View.VISIBLE

        joinLL.visibility = View.GONE

        playersList.visibility = View.VISIBLE

        hostBtn.visibility = View.GONE
        joinBtn.visibility = View.GONE

        roomCodeTv.text = roomCode

        roomManager.listenLobby()
        roomManager.listenRoomState()

    }

    override fun onRoomJoined(roomCode: String) {

        hostLL.visibility = View.GONE

        joinLL.visibility = View.GONE

        playersList.visibility = View.VISIBLE

        hostBtn.visibility = View.GONE
        joinBtn.visibility = View.GONE

        roomManager.listenLobby()
        roomManager.listenRoomState()

    }

    override fun onLobbyUpdated(players: List<LobbyPlayer>) {

        adapter.updatePlayers(players)

    }

    override fun onGameStarted(roomCode: String) {

        android.util.Log.d("GAME_FLOW", "onGameStarted() called")

        hideLoading()

        Toast.makeText(
            this,
            "Opening Game...",
            Toast.LENGTH_SHORT
        ).show()

        startActivity(
            android.content.Intent(
                this,
                MainActivity::class.java
            )
        )

        finish()

    }

    override fun onError(message: String) {

        Toast.makeText(
            this,
            message,
            Toast.LENGTH_SHORT
        ).show()

    }
    override fun onReadyStateChanged(
        readyPlayers: Int,
        totalPlayers: Int,
        allReady: Boolean
    ) {

        if (!allReady)
            return

        showLoading()

        updateLoading("Waiting for host...")

        roomManager.beginGame()
    }
    private fun showLoading() {

        loadingOverlay.visibility = View.VISIBLE

    }

    private fun hideLoading() {

        loadingOverlay.visibility = View.GONE

    }

    private fun updateLoading(message: String) {

        loadingStatusTv.text = message

    }
    override fun onHostShouldInitializeGame() {

        GameInitializer(
            roomManager.getCurrentRoom()!!,
            object : GameInitializationCallback {

                override fun onStatusChanged(message: String) {
                    updateLoading(message)
                }

                override fun onCompleted() {

                    android.util.Log.d("GAME_FLOW", "GameInitializer completed")

                    updateLoading("Waiting for players...")

                }

                override fun onError(message: String) {
                    hideLoading()
                    Toast.makeText(
                        this@HomeActivity,
                        message,
                        Toast.LENGTH_SHORT
                    ).show()
                }

            }
        ).initializeGame()

    }
    override fun onPause() {
        super.onPause()
        mediaPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        mediaPlayer?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
    }

}