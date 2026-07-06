package com.voxcom.tikitopple

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import java.util.Collections

class MainActivity : AppCompatActivity() {

    private lateinit var board: FrameLayout
    private lateinit var arrayText: TextView

    data class Tiki(
        val id: Int,
        val imageRes: Int
    ) {
        lateinit var view: ImageView
    }

    private val tikis = mutableListOf<Tiki>()
    private val boardOrder = mutableListOf<Tiki>()
    private var blockSpacing = 0f
    private var sideOffset = 0f

    companion object {
        const val ANIMATION_DURATION = 220L
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        board = findViewById(R.id.board)
        arrayText = findViewById(R.id.array)

        createTikis()

        board.post {
            calculateBoardMetrics()
            createViews()
            refreshBoard()
            updateArrayText()
        }
    }

    private fun createTikis() {
        val images = listOf(
            R.drawable.t1, R.drawable.t2, R.drawable.t3,
            R.drawable.t4, R.drawable.t5, R.drawable.t6,
            R.drawable.t7, R.drawable.t8, R.drawable.t9
        )
        images.forEachIndexed { index, res ->
            val t = Tiki(index + 1, res)
            tikis.add(t)
            boardOrder.add(t)
        }
    }

    private var tikiWidth = 0
    private var tikiHeight = 0

    private fun calculateBoardMetrics() {

        val boardWidth = board.width.toFloat()
        val boardHeight = board.height.toFloat()

        // 9 tikis stacked with no gap
        tikiHeight = (boardHeight / 9f).toInt()

        // Aspect ratio 5:8 (height : width)
        tikiWidth = (tikiHeight * 8f / 5f).toInt()

        blockSpacing = tikiHeight.toFloat()

        sideOffset = boardWidth * 0.35f
    }

    private fun createViews() {
        board.removeAllViews()

        boardOrder.forEach { tiki ->

            val img = ImageView(this)

            val params = FrameLayout.LayoutParams(tikiWidth, tikiHeight)
            params.gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL

            img.layoutParams = params
            img.setImageResource(tiki.imageRes)
            img.scaleType = ImageView.ScaleType.FIT_XY

            img.setOnClickListener {
                tikiToss(tiki)
            }

            board.addView(img)
            tiki.view = img
        }
    }

    private fun tikiUp1(selected: Tiki) {

        val oldIndex = boardOrder.indexOf(selected)
        if (oldIndex == 0) return

        val newIndex = oldIndex - 1
        val above = boardOrder[newIndex]

        setEnabled(false)

        val moveRight = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            sideOffset
        ).apply { duration = ANIMATION_DURATION }

        val moveAboveDown = ObjectAnimator.ofFloat(
            above.view,
            View.TRANSLATION_Y,
            (newIndex + 1) * blockSpacing
        ).apply { duration = ANIMATION_DURATION }

        val moveSelectedUp = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_Y,
            newIndex * blockSpacing
        ).apply { duration = ANIMATION_DURATION }

        val moveLeft = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            0f
        ).apply { duration = ANIMATION_DURATION }

        AnimatorSet().apply {

            playSequentially(
                moveRight,
                moveAboveDown,
                moveSelectedUp,
                moveLeft
            )

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {

                    Collections.swap(boardOrder, oldIndex, newIndex)

                    refreshBoard()
                    updateArrayText()

                    setEnabled(true)
                }
            })

            start()
        }
    }

    private fun tikiUp2(selected: Tiki) {

        val oldIndex = boardOrder.indexOf(selected)
        if (oldIndex < 2) return

        val targetIndex = oldIndex - 2

        val first = boardOrder[targetIndex]
        val second = boardOrder[targetIndex + 1]

        setEnabled(false)

        val moveRight = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            sideOffset
        ).apply { duration = ANIMATION_DURATION }

        val moveFirstDown = ObjectAnimator.ofFloat(
            first.view,
            View.TRANSLATION_Y,
            getYForIndex(targetIndex + 1)
        ).apply { duration = ANIMATION_DURATION }

        val moveSecondDown = ObjectAnimator.ofFloat(
            second.view,
            View.TRANSLATION_Y,
            getYForIndex(targetIndex + 2)
        ).apply { duration = ANIMATION_DURATION }

        val moveSelectedUp = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_Y,
            getYForIndex(targetIndex)
        ).apply { duration = ANIMATION_DURATION }

        val moveLeft = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            0f
        ).apply { duration = ANIMATION_DURATION }

        AnimatorSet().apply {

            playSequentially(
                moveRight,
                AnimatorSet().apply {
                    playTogether(moveFirstDown, moveSecondDown)
                },
                moveSelectedUp,
                moveLeft
            )

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {

                    boardOrder.removeAt(oldIndex)
                    boardOrder.add(targetIndex, selected)

                    refreshBoard()
                    updateArrayText()
                    setEnabled(true)
                }
            })

            start()
        }
    }

    private fun tikiUp3(selected: Tiki) {

        val oldIndex = boardOrder.indexOf(selected)
        if (oldIndex < 3) return

        val targetIndex = oldIndex - 3

        val first = boardOrder[targetIndex]
        val second = boardOrder[targetIndex + 1]
        val third = boardOrder[targetIndex + 2]

        setEnabled(false)

        val moveRight = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            sideOffset
        ).apply { duration = ANIMATION_DURATION }

        val moveFirstDown = ObjectAnimator.ofFloat(
            first.view,
            View.TRANSLATION_Y,
            getYForIndex(targetIndex + 1)
        ).apply { duration = ANIMATION_DURATION }

        val moveSecondDown = ObjectAnimator.ofFloat(
            second.view,
            View.TRANSLATION_Y,
            getYForIndex(targetIndex + 2)
        ).apply { duration = ANIMATION_DURATION }

        val moveThirdDown = ObjectAnimator.ofFloat(
            third.view,
            View.TRANSLATION_Y,
            getYForIndex(targetIndex + 3)
        ).apply { duration = ANIMATION_DURATION }

        val moveSelectedUp = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_Y,
            getYForIndex(targetIndex)
        ).apply { duration = ANIMATION_DURATION }

        val moveLeft = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            0f
        ).apply { duration = ANIMATION_DURATION }

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

                    boardOrder.removeAt(oldIndex)
                    boardOrder.add(targetIndex, selected)

                    refreshBoard()
                    updateArrayText()
                    setEnabled(true)
                }
            })

            start()
        }
    }

    private fun tikiToss(selected: Tiki) {

        val oldIndex = boardOrder.indexOf(selected)
        if (oldIndex == boardOrder.lastIndex) return   // Already at bottom

        setEnabled(false)

        val moveRight = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            sideOffset
        ).apply { duration = ANIMATION_DURATION }

        // Move every tiki below the selected one up by one position
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
        ).apply { duration = ANIMATION_DURATION }

        val moveLeft = ObjectAnimator.ofFloat(
            selected.view,
            View.TRANSLATION_X,
            0f
        ).apply { duration = ANIMATION_DURATION }

        AnimatorSet().apply {

            playSequentially(
                moveRight,
                moveOthers,
                moveSelectedDown,
                moveLeft
            )

            addListener(object : AnimatorListenerAdapter() {
                override fun onAnimationEnd(animation: Animator) {

                    boardOrder.removeAt(oldIndex)
                    boardOrder.add(selected)

                    refreshBoard()
                    updateArrayText()

                    setEnabled(true)
                }
            })

            start()
        }
    }

    private fun refreshBoard() {
        boardOrder.forEachIndexed { index, tiki ->
            tiki.view.translationX = 0f
            tiki.view.translationY = getYForIndex(index)
        }
    }

    private fun updateArrayText() {
        arrayText.text = boardOrder.joinToString(" ") { it.id.toString() }
    }

    private fun setEnabled(enabled: Boolean) {
        boardOrder.forEach { it.view.isEnabled = enabled }
    }

    private fun destroyTiki(tiki: Tiki) {

        setEnabled(false)

        val frames = listOf(
            R.drawable.ta,
            R.drawable.tb,
            R.drawable.tc,
            R.drawable.td
        )

        val frameDuration = 120L

        tiki.view.postDelayed({
            tiki.view.setImageResource(frames[0])
        }, frameDuration)

        tiki.view.postDelayed({
            tiki.view.setImageResource(frames[1])
        }, frameDuration * 2)

        tiki.view.postDelayed({
            tiki.view.setImageResource(frames[2])
        }, frameDuration * 3)

        tiki.view.postDelayed({
            tiki.view.setImageResource(frames[3])
        }, frameDuration * 4)

        tiki.view.postDelayed({
            val removedIndex = boardOrder.indexOf(tiki)

            board.removeView(tiki.view)
            boardOrder.removeAt(removedIndex)

            // Simply refresh the board; gravity math handles the rest!
            refreshBoard()
            updateArrayText()

            setEnabled(true)
        }, frameDuration * 5)
    }
    private fun getYForIndex(index: Int): Float {
        // Stacks items up from the bottom of the board
        return board.height - (boardOrder.size - index) * blockSpacing
    }
}