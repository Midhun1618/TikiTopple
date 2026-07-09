package com.voxcom.tikitopple

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast

import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.*

import com.voxcom.tikitopple.manager.*
import com.voxcom.tikitopple.model.GameData

class MainActivity : AppCompatActivity() {

    // NOTE: createTikis(), calculateBoardMetrics(), createViews(), getYForIndex(),
    // updateArrayText(), and showSecretCardDialog() were NOT included in the source
    // file you pasted. Their call sites below are preserved exactly as they were;
    // keep your existing implementations for them unchanged. None of the multiplayer
    // sync / animation fixes in this refactor depend on their internals.

    private lateinit var board: FrameLayout
    private lateinit var arrayText: TextView

    data class Tiki(
        val id: Int,
        val imageRes: Int
    ) {
        lateinit var view: ImageView
    }

    private val tikis = mutableListOf<Tiki>()

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

    // True while a board animation is actively playing.
    private var isAnimating = false

    // If a Firebase board update arrives while we're mid-animation, it's stashed
    // here and processed as soon as the current animation completes.
    private var pendingGameForBoard: GameData? = null

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
        arrayText = findViewById(R.id.array)

        actionCardBtn = findViewById(R.id.actionCardBtn)
        secretCardBtn = findViewById(R.id.secretCardBtn)

        createTikis()

        board.post {

            calculateBoardMetrics()

            createViews()

        }

        setupActionCardButton()

        setupSecretCardButton()

        listenGame()

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

    private fun setEnabled(enabled: Boolean) {

        boardOrder.forEach {

            it.view.isEnabled = enabled

            it.view.alpha =
                if (enabled) 1f else 0.5f

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

                    // -----------------------------
                    // Update Turn (independent of board animation state)
                    // -----------------------------

                    val myTurn =
                        game.currentTurn == uid

                    if (!isAnimating) {
                        setEnabled(myTurn)
                    }

                    // -----------------------------
                    // Update Secret Card Button
                    // -----------------------------

                    val me = game.players[uid]

                    me?.let {

                        secretCardBtn.setImageResource(
                            SecretCardManager.getImage(
                                it.secretCard
                            )
                        )

                    }

                    // -----------------------------
                    // Update Action Card Button
                    // -----------------------------

                    updateActionCards()

                    // -----------------------------
                    // Check Winner
                    // -----------------------------

                    if (game.round > 3) {

                        Toast.makeText(
                            this@MainActivity,
                            "Game Finished",
                            Toast.LENGTH_SHORT
                        ).show()

                    }

                    // -----------------------------
                    // Sync Board (queued if an animation is already playing)
                    // -----------------------------

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

    private fun updateActionCards() {

        val game = gameData ?: return

        val me = game.players[uid] ?: return

        if (selectedCard !in me.cards) {

            selectedCard = null

            actionCardBtn.setImageResource(R.drawable.action_card_btn)

        }

    }

    // ---------------------------------------------------------------------
    // Board synchronization
    // ---------------------------------------------------------------------

    private fun handleBoardUpdate(game: GameData) {

        if (isAnimating) {

            pendingGameForBoard = game

            return

        }

        processBoardUpdate(game)

    }

    private fun processBoardUpdate(game: GameData) {

        // boardOrder is populated by createViews() with the initial layout.
        // If it hasn't been created yet, there's nothing to diff against.
        if (boardOrder.isEmpty()) return

        val oldBoard = boardOrder.map { it.id }
        val newBoard = game.board

        if (oldBoard == newBoard) {
            return
        }

        isAnimating = true

        setEnabled(false)

        animateMove(oldBoard, newBoard, game) {

            applyBoardImmediate(newBoard)

            isAnimating = false

            val stillMyTurn = gameData?.currentTurn == uid

            setEnabled(stillMyTurn)

            val next = pendingGameForBoard

            pendingGameForBoard = null

            if (next != null) {
                processBoardUpdate(next)
            }

        }

    }

    private fun applyBoardImmediate(newBoard: List<Int>) {

        val newOrder = newBoard.map { id -> tikis.first { it.id == id } }

        boardOrder.clear()
        boardOrder.addAll(newOrder)

        refreshBoard()

        updateArrayText()

    }

    private fun refreshBoard() {

        // Any tiki no longer present in boardOrder (e.g. tossed) gets its view
        // pulled off the board.
        val presentIds = boardOrder.map { it.id }.toSet()

        tikis.forEach { tiki ->

            if (tiki.id !in presentIds && tiki.view.parent != null) {

                board.removeView(tiki.view)

            }

        }

        boardOrder.forEachIndexed { index, tiki ->

            tiki.view.animate()
                .translationX(0f)
                .translationY(getYForIndex(index))
                .setDuration(ANIMATION_DURATION)
                .start()

        }

    }

    // ---------------------------------------------------------------------
    // Central animation dispatcher
    // ---------------------------------------------------------------------

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

    // ---------------------------------------------------------------------
    // Turn handling — no game rules here, only Firebase orchestration
    // ---------------------------------------------------------------------

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
        actionCardBtn.setImageResource(R.drawable.action_card_btn)

    }
    private fun calculateBoardMetrics() {

        tikiWidth = (board.width * 0.45f).toInt()

        val sample = resources.getDrawable(
            tikis.first().imageRes,
            theme
        )

        val ratio =
            sample.intrinsicHeight.toFloat() /
                    sample.intrinsicWidth.toFloat()

        tikiHeight = (tikiWidth * ratio).toInt()

        blockSpacing = tikiHeight * 0.55f

        sideOffset = board.width * 0.28f

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

        val bottomY = board.height - tikiHeight.toFloat()

        return bottomY - ((boardOrder.size - 1 - index) * blockSpacing)

    }
    private fun updateArrayText() {

        arrayText.text = boardOrder.joinToString(
            prefix = "[",
            postfix = "]"
        ) { "T${it.id}" }

    }
    private fun showSecretCardDialog() {

        val game = gameData ?: return

        val me = game.players[uid] ?: return

        val dialogView = layoutInflater.inflate(
            R.layout.dialog_secret_card,
            null
        )

        val secretCardImage =
            dialogView.findViewById<ImageView>(R.id.secretCardImage)

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

            image.layoutParams = FrameLayout.LayoutParams(
                tikiWidth,
                tikiHeight
            ).apply {
                gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            }

            image.translationY = getYForIndex(index)

            image.translationX = 0f

            image.setOnClickListener {

                if (isAnimating)
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

        updateArrayText()

    }

    private fun showActionCardDialog() {

        val game = gameData ?: return

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

            // Played cards are marked invisible (not removed) once
            // ActionCardManager.removePlayedCard() drops them from me.cards.
            // They become visible again automatically once a new round
            // deals a fresh hand via ActionCardManager.deal().
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

    // ---------------------------------------------------------------------
    // Animations
    //
    // These only move views and report completion via onFinished().
    // None of them mutate boardOrder — the dispatcher's caller
    // (processBoardUpdate) does that once, after the animation ends.
    // "oldBoard" is passed in explicitly for index lookups; boardOrder
    // itself still mirrors oldBoard at this point since it hasn't been
    // replaced yet.
    // ---------------------------------------------------------------------

    private fun tikiUp1(selected: Tiki, oldBoard: List<Int>, onFinished: () -> Unit) {

        val oldIndex = oldBoard.indexOf(selected.id)

        if (oldIndex <= 0) {
            onFinished()
            return
        }

        val newIndex = oldIndex - 1

        val above = boardOrder[newIndex]

        val moveRight = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            sideOffset
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

        val moveLeft = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            0f
        ).apply {
            duration = ANIMATION_DURATION
        }

        AnimatorSet().apply {

            playSequentially(
                moveRight,
                moveAboveDown,
                moveSelectedUp,
                moveLeft
            )

            addListener(object : AnimatorListenerAdapter() {

                override fun onAnimationEnd(animation: Animator) {

                    onFinished()

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

        val moveRight = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            sideOffset
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

        val moveLeft = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            0f
        ).apply {
            duration = ANIMATION_DURATION
        }

        AnimatorSet().apply {

            playSequentially(
                moveRight,
                AnimatorSet().apply {
                    playTogether(
                        moveFirstDown,
                        moveSecondDown
                    )
                },
                moveSelectedUp,
                moveLeft
            )

            addListener(object : AnimatorListenerAdapter() {

                override fun onAnimationEnd(animation: Animator) {

                    onFinished()

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

        val moveRight = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            sideOffset
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

        val moveLeft = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            0f
        ).apply {
            duration = ANIMATION_DURATION
        }

        AnimatorSet().apply {

            playSequentially(
                moveRight,
                AnimatorSet().apply {
                    playTogether(
                        moveFirstDown,
                        moveSecondDown,
                        moveThirdDown
                    )
                },
                moveSelectedUp,
                moveLeft
            )

            addListener(object : AnimatorListenerAdapter() {

                override fun onAnimationEnd(animation: Animator) {

                    onFinished()

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

        val moveRight = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            sideOffset
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

        val moveLeft = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            0f
        ).apply {
            duration = ANIMATION_DURATION
        }

        AnimatorSet().apply {

            playSequentially(
                moveRight,
                moveOthers,
                moveSelectedDown,
                moveLeft
            )

            addListener(object : AnimatorListenerAdapter() {

                override fun onAnimationEnd(animation: Animator) {

                    onFinished()

                }

            })

            start()

        }

    }

    private fun tikiToss(selected: Tiki, onFinished: () -> Unit) {

        val frames = getToastFrames(selected.id)

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

        // View removal is handled centrally by refreshBoard() once
        // boardOrder is updated to the post-toss board — not here.
        selected.view.postDelayed({
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
}