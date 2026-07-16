package com.voxcom.tikitopple

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.media.MediaPlayer
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.animation.AnimationUtils
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

import com.voxcom.tikitopple.manager.*
import com.voxcom.tikitopple.model.GameData
import com.voxcom.tikitopple.model.GamePhase
import com.voxcom.tikitopple.model.PlayerAvatars

class MainActivity : AppCompatActivity() {
    private lateinit var board: FrameLayout
    private lateinit var playerAvatars: List<ImageView>


    private lateinit var playerPanels: List<ConstraintLayout>

    private lateinit var playerScores: List<TextView>

    private val activeColor = R.drawable.active_green

    private val inactiveColor = R.drawable.nothing

    data class Tiki(
        val id: Int,
        val imageRes: Int
    ) {
        lateinit var view: ImageView
    }

    private val tikis = mutableListOf<Tiki>()
    private val tossedTikiIds = mutableSetOf<Int>()

    private var bgm: MediaPlayer? = null
    private var mediaPlayer: MediaPlayer? = null

    // boardOrder always reflects what is CURRENTLY ON SCREEN.
    // It is only ever replaced AFTER an animation finishes.
    private val boardOrder = mutableListOf<Tiki>()

    private var blockSpacing = 0f
    private var sideOffset = 0f

    private var tikiWidth = 0
    private var tikiHeight = 0

    companion object {
        const val ANIMATION_DURATION = 220L
    }

    private lateinit var actionCardBtn: ImageView
    private lateinit var secretCardBtn: ImageView

    private val auth = FirebaseAuth.getInstance()
    private val database = FirebaseDatabase.getInstance().reference

    private lateinit var roomManager: RoomManager

    private val uid: String
        get() = auth.currentUser!!.uid

    private lateinit var roomCode: String

    private var gameData: GameData? = null

    private var selectedCard: String? = null
    private var selectedTiki: Tiki? = null
    private var isAnimating = false
    private var pendingGameForBoard: GameData? = null

    private lateinit var roundOverlay: View

    private lateinit var roundTitleTv: TextView

    private lateinit var roundScoreTv: TextView

    private lateinit var roundStatusTv: TextView


    private lateinit var winnerOverlay: ConstraintLayout

    private lateinit var winnerTitle: TextView
    private lateinit var winnerScore: TextView
    private lateinit var winnerLeaderboard: TextView

    private lateinit var playAgainBtn: ImageView
    private lateinit var homeBtn: ImageView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        enableEdgeToEdge()

        setContentView(R.layout.activity_main)

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


        roomManager = RoomManager(this, object : RoomCallback {

            override fun onRoomCreated(roomCode: String) {}

            override fun onRoomJoined(roomCode: String) {}

            override fun onLobbyUpdated(players: List<com.voxcom.tikitopple.model.LobbyPlayer>) {}

            override fun onReadyStateChanged(
                readyPlayers: Int,
                totalPlayers: Int,
                allReady: Boolean
            ) {}

            override fun onHostShouldInitializeGame() {}

            override fun onGameStarted(roomCode: String) {}

            override fun onError(message: String) {

                Toast.makeText(
                    this@MainActivity,
                    message,
                    Toast.LENGTH_SHORT
                ).show()

            }

        })

        roomCode = roomManager.getCurrentRoom() ?: run {

            finish()

            return

        }

        board = findViewById(R.id.board)

        actionCardBtn = findViewById(R.id.actionCardBtn)
        secretCardBtn = findViewById(R.id.secretCardBtn)

        playerPanels = listOf(
            findViewById(R.id.player1),
            findViewById(R.id.player2),
            findViewById(R.id.player3),
            findViewById(R.id.player4)
        )

        playerScores = listOf(
            findViewById(R.id.player1Score),
            findViewById(R.id.player2Score),
            findViewById(R.id.player3Score),
            findViewById(R.id.player4Score)
        )

        playerAvatars = listOf(
            findViewById(R.id.player1Avatar),
            findViewById(R.id.player2Avatar),
            findViewById(R.id.player3Avatar),
            findViewById(R.id.player4Avatar)
        )

        roundOverlay = findViewById(R.id.roundOverlay)

        roundTitleTv = findViewById(R.id.roundTitleTv)

        roundScoreTv = findViewById(R.id.roundScoreTv)

        roundStatusTv = findViewById(R.id.roundStatusTv)

        winnerOverlay = findViewById(R.id.winnerOverlay)

        winnerTitle = findViewById(R.id.winnerTitle)
        winnerScore = findViewById(R.id.winnerScore)
        winnerLeaderboard = findViewById(R.id.winnerLeaderboard)

        playAgainBtn = findViewById(R.id.playAgainBtn)
        homeBtn = findViewById(R.id.homeBtn)

        createTikis()

        board.post {

            calculateBoardMetrics()

            createViews()

            gameData?.let { handleBoardUpdate(it) }

        }
        playAgainBtn.setOnClickListener {

            finish()

        }

        homeBtn.setOnClickListener {

            startActivity(Intent(this, HomeActivity::class.java))
            finish()

        }

        setupActionCardButton()

        setupSecretCardButton()

        listenGame()

        bgm = MediaPlayer.create(this, R.raw.game_bgm)
        bgm?.isLooping = true   // Loop forever
        bgm?.setVolume(8f, 8f)   // 100%
        bgm?.start()

    }

    private fun setupActionCardButton() {

        actionCardBtn.setOnClickListener {

            showActionCardDialog()

        }

    }

    private fun setupSecretCardButton() {

        secretCardBtn.setOnClickListener {

            showSecretCardDialog()

        }

    }

    private fun listenGame() {

        database.child("rooms")
            .child(roomCode)
            .child("game")
            .addValueEventListener(object : ValueEventListener {

                override fun onDataChange(snapshot: DataSnapshot) {

                    val game =
                        snapshot.getValue(GameData::class.java)
                            ?: return

                    gameData = game
                    updatePlayerPanels()

//                    val me = game.players[uid]
//
//                    me?.let {
//
//                        secretCardBtn.setImageResource(
//                            SecretCardManager.getImage(
//                                it.secretCard
//                            )
//                        )
//
//                    }

                    updateActionCards()
                    when (game.gamePhase) {

                        GamePhase.GAME_OVER -> {

                            showWinner()

                        }

                        GamePhase.ROUND_RESULTS -> {

                            showRoundResults()
                            tossedTikiIds.clear()
                            restoreAllTikis()

                            database.child("rooms")
                                .child(roomCode)
                                .child("hostUid")
                                .get()
                                .addOnSuccessListener {

                                    val hostUid =
                                        it.getValue(String::class.java)
                                            ?: return@addOnSuccessListener

                                    if (hostUid != uid)
                                        return@addOnSuccessListener

                                    roundOverlay.postDelayed({

                                        val currentGame = gameData ?: return@postDelayed

                                        if (currentGame.gamePhase != GamePhase.ROUND_RESULTS)
                                            return@postDelayed

                                        RoundManager(roomCode)
                                            .startNextRound(currentGame)

                                    },3000)

                                }

                        }

                        GamePhase.MOVE -> {

                            hideRoundOverlay()
                            hideWinnerOverlay()

                        }

                    }

                    if (game.round > 3) {

                        Toast.makeText(
                            this@MainActivity,
                            "Game Finished",
                            Toast.LENGTH_SHORT
                        ).show()

                    }


                    handleBoardUpdate(game)

                }

                override fun onCancelled(error: DatabaseError) {

                    Toast.makeText(
                        this@MainActivity,
                        error.message,
                        Toast.LENGTH_SHORT
                    ).show()

                }

            })
    }

    private fun updatePlayerPanels() {

        val game = gameData ?: return

        val order = game.turnOrder

        playerPanels.forEachIndexed { index, panel ->

            if (index >= order.size) {

                panel.visibility = View.GONE

                return@forEachIndexed

            }

            panel.visibility = View.VISIBLE

            val uid = order[index]

            val player = game.players[uid] ?: return@forEachIndexed

            playerScores[index].text = player.score.toString()
            playerAvatars[index].setImageResource(PlayerAvatars.RES[player.avatarIndex])

            playerAvatars[index].setBackgroundResource(

                if (uid == game.currentTurn)
                    activeColor
                else
                    inactiveColor

            )

        }

        val myTurn = game.currentTurn == uid

        actionCardBtn.alpha =
            if (myTurn) 1f else 0.7f

        actionCardBtn.isEnabled = myTurn

    }

    private fun updateActionCards() {

        val game = gameData ?: return

        val me = game.players[uid] ?: return

        if (selectedCard !in me.cards) {

            selectedCard = null

            actionCardBtn.setImageResource(R.drawable.action_cards)

        }

    }

    private fun handleBoardUpdate(game: GameData) {

        if (isAnimating) {

            pendingGameForBoard = game

            return

        }

        processBoardUpdate(game)

    }

    private fun processBoardUpdate(game: GameData) {

        if (boardOrder.isEmpty()) return

        val oldBoard = boardOrder.map { it.id }
        val newBoard = game.board

        if (oldBoard == newBoard) {
            return
        }

        isAnimating = true

        animateMove(oldBoard, newBoard, game) {

            applyBoardImmediate(newBoard)

            isAnimating = false

            checkRoundFinished()

            val next = pendingGameForBoard

            pendingGameForBoard = null

            if (next != null) {

                processBoardUpdate(next)

            }

        }

    }

    private fun checkRoundFinished() {

        val game = gameData ?: return

        if (game.gamePhase != GamePhase.MOVE)
            return

        val allFinished = game.players.values.all {

            !ActionCardManager.hasCards(it.cards)

        }

        if (!allFinished)
            return

        val roomRef =
            database.child("rooms").child(roomCode)

        roomRef.child("hostUid")
            .get()
            .addOnSuccessListener {

                val hostUid =
                    it.getValue(String::class.java)
                        ?: return@addOnSuccessListener

                if (uid != hostUid)
                    return@addOnSuccessListener

                onRoundFinished()

            }

    }
    private fun onRoundFinished() {

        val game = gameData ?: return

        val playerSecrets =
            game.players.mapValues {

                it.value.secretCard

            }

        val roundScores =
            ScoreManager.calculateAllScores(

                playerSecrets,

                game.board

            )

        val updates = hashMapOf<String, Any>()

        roundScores.forEach { (playerUid, score) ->

            val current =
                game.players[playerUid]?.score ?: 0

            updates[
                "rooms/$roomCode/game/players/$playerUid/score"
            ] = current + score

        }

        updates["rooms/$roomCode/game/roundScores"] =
            roundScores

        updates["rooms/$roomCode/game/gamePhase"] =
            GamePhase.ROUND_RESULTS

        database.updateChildren(updates)

    }

    private fun showRoundResults() {

        val game = gameData ?: return

        roundTitleTv.text = "ROUND ${game.round} COMPLETE"

        val builder = StringBuilder()

        val sortedPlayers =
            game.players.values.sortedByDescending { it.score }

        sortedPlayers.forEachIndexed { index, player ->

            val roundScore =
                game.roundScores[player.uid] ?: 0

            val medal = when(index) {
                0 -> "🥇 "
                1 -> "🥈 "
                2 -> "🥉 "
                else -> ""
            }

            builder.append(
                medal +
                        player.name +
                        "\n" +
                        "+$roundScore    Total : ${player.score}\n\n"
            )

        }

        roundScoreTv.text = builder.toString()

        roundStatusTv.text = "Preparing next round..."

        roundOverlay.alpha = 0f
        roundOverlay.visibility = View.VISIBLE

        roundOverlay.animate()
            .alpha(1f)
            .setDuration(250)
            .start()

    }

    private fun hideRoundOverlay() {

        roundOverlay.animate()
            .alpha(0f)
            .setDuration(250)
            .withEndAction {

                roundOverlay.visibility = View.GONE

                roundOverlay.alpha = 1f

            }
            .start()

    }
    private fun applyBoardImmediate(newBoard: List<Int>) {
        val newOrder = newBoard.map { id -> tikis.first { it.id == id } }
        boardOrder.clear()
        boardOrder.addAll(newOrder)
        refreshBoard()
    }
    private fun refreshBoard() {
        boardOrder.forEachIndexed { index, tiki ->
            tiki.view.visibility =
                if (tiki.id in tossedTikiIds) View.INVISIBLE else View.VISIBLE

            tiki.view.animate()
                .translationX(sideOffset)
                .translationY(getYForIndex(index))
                .setDuration(ANIMATION_DURATION)
                .start()
        }
    }
    private fun animateMove(
        oldBoard: List<Int>,
        newBoard: List<Int>,
        game: GameData,
        onFinished: () -> Unit
    ) {

        val move = game.lastMove

        if (move.playerUid.isBlank()) {
            onFinished()
            return
        }

        val tiki = tikis.firstOrNull { it.id == move.selectedTiki }

        if (tiki == null) {
            onFinished()
            return
        }

        when (move.actionCard) {

            "UP1" -> tikiUp1(tiki, oldBoard, onFinished)

            "UP2" -> tikiUp2(tiki, oldBoard, onFinished)

            "UP3" -> tikiUp3(tiki, oldBoard, onFinished)

            "TOPPLE1",
            "TOPPLE2" -> tikiTopple(tiki, oldBoard, onFinished)

            "TOAST" -> tikiToss(tiki, onFinished)

            else -> onFinished()

        }

    }
    private fun playTurn() {

        val game = gameData ?: return

        val tiki = selectedTiki ?: return

        val card = selectedCard ?: return

        val me = game.players[uid] ?: return

        if (game.currentTurn != uid) {

            Toast.makeText(
                this,
                "Not your turn.",
                Toast.LENGTH_SHORT
            ).show()

            return

        }

        val moveResult = BoardManager.playMove(

            board = game.board,

            card = card,

            selectedTiki = tiki.id,

            isFirstMove = me.cards.size == ActionCardManager.deal().size

        )

        if (!moveResult.success) {

            Toast.makeText(
                this,
                moveResult.message,
                Toast.LENGTH_SHORT
            ).show()

            return

        }

        val remainingCards = ActionCardManager.removePlayedCard(
            me.cards,
            card
        )

        val nextTurn = TurnManager.nextPlayer(
            game.turnOrder,
            uid
        )

        val updates = hashMapOf<String, Any>()

        updates["rooms/$roomCode/game/board"] =
            moveResult.board

        updates["rooms/$roomCode/game/currentTurn"] =
            nextTurn

        updates["rooms/$roomCode/game/players/$uid/cards"] =
            remainingCards

        updates["rooms/$roomCode/game/lastMove/playerUid"] =
            uid

        updates["rooms/$roomCode/game/lastMove/actionCard"] =
            card

        updates["rooms/$roomCode/game/lastMove/selectedTiki"] =
            tiki.id

        database.updateChildren(updates)
            .addOnFailureListener {

                Toast.makeText(
                    this,
                    it.message,
                    Toast.LENGTH_SHORT
                ).show()

            }

        selectedCard = null
        selectedTiki = null
        actionCardBtn.setImageResource(R.drawable.action_cards)

    }
    private fun calculateBoardMetrics() {

        val usableWidth =
            board.width -
                    board.paddingLeft -
                    board.paddingRight

        val usableHeight =
            board.height -
                    board.paddingTop -
                    board.paddingBottom

        val maxHeightPerTiki = usableHeight / 9

        val maxWidthPerTiki = (usableWidth * 0.75f).toInt()

        val widthFromHeight =
            (maxHeightPerTiki * 1.5f).toInt()

        if (widthFromHeight <= maxWidthPerTiki) {

            tikiHeight = maxHeightPerTiki
            tikiWidth = widthFromHeight

        } else {

            tikiWidth = maxWidthPerTiki
            tikiHeight = (tikiWidth / 1.5f).toInt()

        }

        blockSpacing = tikiHeight.toFloat()

        sideOffset =
            ((usableWidth - tikiWidth) / 2f)

    }
    private fun createTikis() {

        tikis.clear()

        tikis.add(Tiki(1, R.drawable.t1))
        tikis.add(Tiki(2, R.drawable.t2))
        tikis.add(Tiki(3, R.drawable.t3))
        tikis.add(Tiki(4, R.drawable.t4))
        tikis.add(Tiki(5, R.drawable.t5))
        tikis.add(Tiki(6, R.drawable.t6))
        tikis.add(Tiki(7, R.drawable.t7))
        tikis.add(Tiki(8, R.drawable.t8))
        tikis.add(Tiki(9, R.drawable.t9))

    }
    private fun getYForIndex(index: Int): Float {

        val totalSlots = tikis.size
        val visibleCount = boardOrder.size
        val emptySlotsAbove = totalSlots - visibleCount

        return board.paddingTop + (emptySlotsAbove + index) * blockSpacing
    }
    private fun showSecretCardDialog() {

        val game = gameData ?: return

        val me = game.players[uid] ?: return

        val dialogView = layoutInflater.inflate(
            R.layout.dialog_secret_card,
            null
        )

        val secretCardImage = dialogView.findViewById<ImageView>(R.id.secretCardImage)
        val rotatinglight = dialogView.findViewById<ImageView>(R.id.rotatImg)

        val rotate = AnimationUtils.loadAnimation(this, R.anim.rotater)
        rotatinglight.startAnimation(rotate)

        secretCardImage.setImageResource(
            SecretCardManager.getImage(me.secretCard)
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        dialog.show()

        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

    }
    private fun createViews() {

        board.removeAllViews()

        boardOrder.clear()

        tikis.forEachIndexed { index, tiki ->

            val image = ImageView(this)

            image.setImageResource(tiki.imageRes)

            val params = FrameLayout.LayoutParams(
                tikiWidth,
                tikiHeight
            )

            params.gravity = Gravity.TOP or Gravity.START

            image.layoutParams = params

            image.translationX = sideOffset

            image.translationY = getYForIndex(index)

            image.setOnClickListener {

                if (isAnimating)
                    return@setOnClickListener

                val game = gameData ?: return@setOnClickListener

                if (game.currentTurn != uid)
                    return@setOnClickListener

                if (selectedCard == null) {

                    Toast.makeText(
                        this,
                        "Select an action card first.",
                        Toast.LENGTH_SHORT
                    ).show()

                    return@setOnClickListener

                }

                selectedTiki = tiki

                playTurn()

            }

            tiki.view = image

            board.addView(image)

            boardOrder.add(tiki)

        }

    }
    private fun showActionCardDialog() {

        val game = gameData ?: return

        if (game.currentTurn != uid) {

            Toast.makeText(
                this,
                "Wait for your turn.",
                Toast.LENGTH_SHORT
            ).show()

            return

        }

        val me = game.players[uid] ?: return

        val dialogView = layoutInflater.inflate(
            R.layout.dialog_action_card,
            null
        )

        val dialog = androidx.appcompat.app.AlertDialog.Builder(this)
            .setView(dialogView)
            .create()

        fun setupCard(
            imageId: Int,
            cardId: String,
            drawable: Int
        ) {

            val img = dialogView.findViewById<ImageView>(imageId)

            img.setImageResource(drawable)

            if (me.cards.contains(cardId)) {

                img.visibility = View.VISIBLE
                img.alpha = 1f

                img.setOnClickListener {

                    selectedCard = cardId

                    actionCardBtn.setImageResource(drawable)

                    dialog.dismiss()

                }

            } else {

                img.visibility = View.INVISIBLE

            }

        }

        setupCard(R.id.card1, "UP1", R.drawable.a_1)
        setupCard(R.id.card2, "UP2", R.drawable.a_2)
        setupCard(R.id.card3, "UP3", R.drawable.a_3)
        setupCard(R.id.card4, "TOPPLE1", R.drawable.a_t)
        setupCard(R.id.card5, "TOPPLE2", R.drawable.a_t)
        setupCard(R.id.card6, "TOAST", R.drawable.a_x)

        dialog.show()

        dialog.window?.setBackgroundDrawable(
            ColorDrawable(Color.TRANSPARENT)
        )

    }
    private fun tikiUp1(selected: Tiki, oldBoard: List<Int>, onFinished: () -> Unit) {

        val oldIndex = oldBoard.indexOf(selected.id)

        if (oldIndex <= 0) {
            onFinished()
            return
        }

        val newIndex = oldIndex - 1
        val above = boardOrder[newIndex]

        val moveLeft = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            0f
        ).apply {
            duration = ANIMATION_DURATION
        }

        val moveAboveDown = ObjectAnimator.ofFloat(
            above.view,
            View.TRANSLATION_Y,
            getYForIndex(oldIndex)
        ).apply {
            duration = ANIMATION_DURATION
        }

        val moveSelectedUp = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_Y,
            getYForIndex(newIndex)
        ).apply {
            duration = ANIMATION_DURATION
        }

        val moveRight = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            sideOffset
        ).apply {
            duration = ANIMATION_DURATION
        }

        AnimatorSet().apply {

            playSequentially(
                moveLeft,
                moveAboveDown,
                moveSelectedUp,
                moveRight
            )

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {
                    onFinished()
                }
            })
            moveLeft.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    moveSfx()
                }
            })

            moveRight.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    moveSfx()
                }
            })

            start()
        }
    }
    private fun tikiUp2(selected: Tiki, oldBoard: List<Int>, onFinished: () -> Unit) {

        val oldIndex = oldBoard.indexOf(selected.id)

        if (oldIndex < 2) {
            onFinished()
            return
        }

        val targetIndex = oldIndex - 2

        val first = boardOrder[targetIndex]
        val second = boardOrder[targetIndex + 1]

        val moveLeft = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            0f
        ).apply {
            duration = ANIMATION_DURATION
        }

        val moveFirstDown = ObjectAnimator.ofFloat(
            first.view,
            View.TRANSLATION_Y,
            getYForIndex(targetIndex + 1)
        ).apply {
            duration = ANIMATION_DURATION
        }

        val moveSecondDown = ObjectAnimator.ofFloat(
            second.view,
            View.TRANSLATION_Y,
            getYForIndex(targetIndex + 2)
        ).apply {
            duration = ANIMATION_DURATION
        }

        val moveSelectedUp = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_Y,
            getYForIndex(targetIndex)
        ).apply {
            duration = ANIMATION_DURATION
        }

        val moveRight = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            sideOffset
        ).apply {
            duration = ANIMATION_DURATION
        }

        AnimatorSet().apply {

            playSequentially(
                moveLeft,
                AnimatorSet().apply {
                    playTogether(
                        moveFirstDown,
                        moveSecondDown
                    )
                },
                moveSelectedUp,
                moveRight
            )

            addListener(object : AnimatorListenerAdapter() {

                override fun onAnimationEnd(animation: Animator) {

                    onFinished()

                }

            })
            moveLeft.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    moveSfx()
                }
            })

            moveRight.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    moveSfx()
                }
            })

            start()

        }

    }

    private fun tikiUp3(selected: Tiki, oldBoard: List<Int>, onFinished: () -> Unit) {

        val oldIndex = oldBoard.indexOf(selected.id)

        if (oldIndex < 3) {
            onFinished()
            return
        }

        val targetIndex = oldIndex - 3

        val first = boardOrder[targetIndex]
        val second = boardOrder[targetIndex + 1]
        val third = boardOrder[targetIndex + 2]

        val moveLeft = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            0f
        ).apply {
            duration = ANIMATION_DURATION
        }

        val moveFirstDown = ObjectAnimator.ofFloat(
            first.view,
            View.TRANSLATION_Y,
            getYForIndex(targetIndex + 1)
        ).apply {
            duration = ANIMATION_DURATION
        }

        val moveSecondDown = ObjectAnimator.ofFloat(
            second.view,
            View.TRANSLATION_Y,
            getYForIndex(targetIndex + 2)
        ).apply {
            duration = ANIMATION_DURATION
        }

        val moveThirdDown = ObjectAnimator.ofFloat(
            third.view,
            View.TRANSLATION_Y,
            getYForIndex(targetIndex + 3)
        ).apply {
            duration = ANIMATION_DURATION
        }

        val moveSelectedUp = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_Y,
            getYForIndex(targetIndex)
        ).apply {
            duration = ANIMATION_DURATION
        }

        val moveRight = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            sideOffset
        ).apply {
            duration = ANIMATION_DURATION
        }

        AnimatorSet().apply {

            playSequentially(
                moveLeft,
                AnimatorSet().apply {
                    playTogether(
                        moveFirstDown,
                        moveSecondDown,
                        moveThirdDown
                    )
                },
                moveSelectedUp,
                moveRight
            )

            addListener(object : AnimatorListenerAdapter() {

                override fun onAnimationEnd(animation: Animator) {

                    onFinished()

                }

            })
            moveLeft.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    moveSfx()
                }
            })

            moveRight.addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationStart(animation: Animator) {
                    moveSfx()
                }
            })

            start()

        }

    }

    private fun tikiTopple(selected: Tiki, oldBoard: List<Int>, onFinished: () -> Unit) {

        val oldIndex = oldBoard.indexOf(selected.id)

        if (oldIndex == boardOrder.lastIndex) {
            onFinished()
            return
        }

        val moveLeft = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            0f
        ).apply {
            duration = ANIMATION_DURATION
        }

        val moveOthers = AnimatorSet()

        val animators = mutableListOf<Animator>()

        for (i in oldIndex + 1 until boardOrder.size) {

            val tiki = boardOrder[i]

            animators += ObjectAnimator.ofFloat(
                tiki.view,
                View.TRANSLATION_Y,
                getYForIndex(i - 1)
            ).apply {
                duration = ANIMATION_DURATION
            }

        }

        moveOthers.playTogether(animators)

        val moveSelectedDown = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_Y,
            getYForIndex(boardOrder.lastIndex)
        ).apply {
            duration = ANIMATION_DURATION
        }

        val moveRight = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            sideOffset
        ).apply {
            duration = ANIMATION_DURATION
        }

        AnimatorSet().apply {

            playSequentially(
                moveLeft,
                moveOthers,
                moveSelectedDown,
                moveRight
            )

            addListener(object : AnimatorListenerAdapter() {

                override fun onAnimationEnd(animation: Animator) {

                    onFinished()

                }

            })

            start()

        }
        moveLeft.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                moveSfx()
            }
        })

        moveRight.addListener(object : AnimatorListenerAdapter() {
            override fun onAnimationStart(animation: Animator) {
                moveSfx()
            }
        })

    }
    private fun tikiToss(selected: Tiki, onFinished: () -> Unit) {

        val frames = getToastFrames(selected.id)
        tossedTikiIds.add(selected.id)
        smashSfx()


        if (frames.isEmpty()) {
            onFinished()
            return
        }

        val frameDuration = 120L

        selected.view.postDelayed({
            selected.view.setImageResource(frames[0])
        }, frameDuration)

        selected.view.postDelayed({
            selected.view.setImageResource(frames[1])
        }, frameDuration * 2)

        selected.view.postDelayed({
            selected.view.setImageResource(frames[2])
        }, frameDuration * 3)

        selected.view.postDelayed({
            selected.view.setImageResource(frames[3])
        }, frameDuration * 4)

        selected.view.postDelayed({

            // Restore original tiki image
            selected.view.setImageResource(selected.imageRes)

            // Hide the toasted tiki
            selected.view.visibility = View.INVISIBLE

            onFinished()

        }, frameDuration * 5)

    }

    private fun getToastFrames(tikiId: Int): List<Int> {

        return when (tikiId) {

            1 -> listOf(
                R.drawable.ta,
                R.drawable.tb,
                R.drawable.tc,
                R.drawable.td
            )

            2 -> listOf(
                R.drawable.ta,
                R.drawable.tb,
                R.drawable.tc,
                R.drawable.td
            )

            3 -> listOf(
                R.drawable.ta,
                R.drawable.tb,
                R.drawable.tc,
                R.drawable.td
            )

            4 -> listOf(
                R.drawable.ta,
                R.drawable.tb,
                R.drawable.tc,
                R.drawable.td
            )

            5 -> listOf(
                R.drawable.ta,
                R.drawable.tb,
                R.drawable.tc,
                R.drawable.td
            )

            6 -> listOf(
                R.drawable.ta,
                R.drawable.tb,
                R.drawable.tc,
                R.drawable.td
            )

            7 -> listOf(
                R.drawable.ta,
                R.drawable.tb,
                R.drawable.tc,
                R.drawable.td
            )

            8 -> listOf(
                R.drawable.ta,
                R.drawable.tb,
                R.drawable.tc,
                R.drawable.td
            )

            9 -> listOf(
                R.drawable.ta,
                R.drawable.tb,
                R.drawable.tc,
                R.drawable.td
            )

            else -> emptyList()

        }
    }

    private fun showWinner() {

        val game = gameData ?: return

        winnerOverlay.visibility = View.VISIBLE

        val ranking =
            game.players.values
                .sortedByDescending { it.score }

        val winner = ranking.first()

        winnerTitle.text = winner.name

        winnerScore.text = "${winner.score} Points"

        val leaderboard = StringBuilder()

        ranking.forEachIndexed { index, player ->

            leaderboard.append(
                "${index + 1}. ${player.name}    ${player.score}"
            )

            if (index != ranking.lastIndex)
                leaderboard.append("\n")

        }

        winnerLeaderboard.text = leaderboard.toString()

        actionCardBtn.isEnabled = false
        secretCardBtn.isEnabled = false

    }

    private fun hideWinnerOverlay() {

        winnerOverlay.visibility = View.GONE

        actionCardBtn.isEnabled = true
        secretCardBtn.isEnabled = true

    }

    private fun restoreAllTikis() {

        tikis.forEach {

            it.view.visibility = View.VISIBLE
            it.view.setImageResource(it.imageRes)

        }

    }
    private fun moveSfx() {
        bgm?.setVolume(0.3f, 0.3f)
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer.create(this, R.raw.move)
        mediaPlayer?.start()

        mediaPlayer?.setOnCompletionListener {
            bgm?.setVolume(0.8f, 0.8f)
            it.release()
            mediaPlayer = null
        }
    }
    private fun smashSfx() {
        bgm?.setVolume(0.3f, 0.3f)
        mediaPlayer?.release()

        mediaPlayer = MediaPlayer.create(this, R.raw.tiki_smash)
        mediaPlayer?.start()

        mediaPlayer?.setOnCompletionListener {
            bgm?.setVolume(0.8f, 0.8f)
            it.release()
            mediaPlayer = null
        }
    }
    override fun onPause() {
        super.onPause()
        bgm?.pause()
    }

    override fun onResume() {
        super.onResume()
        bgm?.start()
    }

    override fun onDestroy() {
        super.onDestroy()
        bgm?.release()
        bgm = null
    }
}