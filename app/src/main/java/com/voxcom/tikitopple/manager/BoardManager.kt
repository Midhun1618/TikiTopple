package com.voxcom.tikitopple.manager

import com.voxcom.tikitopple.model.MoveResult

object BoardManager {

    fun playMove(
        board: List<Int>,
        card: String,
        selectedTiki: Int,
        isFirstMove: Boolean
    ): MoveResult {

        return when {

            card == "UP1" ->
                tikiUp(board, selectedTiki, 1)

            card == "UP2" ->
                tikiUp(board, selectedTiki, 2)

            card == "UP3" ->
                tikiUp(board, selectedTiki, 3)

            card.startsWith("TOPPLE") ->
                tikiTopple(board, selectedTiki)

            card == "TOAST" -> {

                if (isFirstMove) {

                    MoveResult(
                        success = false,
                        board = board,
                        message = "Toast cannot be played as the first move."
                    )

                } else if (board.isEmpty() || selectedTiki != board.last()) {

                    MoveResult(
                        success = false,
                        board = board,
                        message = "Only the bottom-most tiki can be toasted."
                    )

                } else {

                    tikiToast(board)

                }

            }

            else -> {

                MoveResult(
                    success = false,
                    board = board,
                    message = "Unknown action card."
                )

            }

        }

    }

    // ==========================================================
    // UP
    // ==========================================================

    private fun tikiUp(
        board: List<Int>,
        tiki: Int,
        amount: Int
    ): MoveResult {

        val newBoard = board.toMutableList()

        val oldIndex = newBoard.indexOf(tiki)

        if (oldIndex == -1) {

            return MoveResult(
                success = false,
                board = board,
                message = "Selected tiki not found."
            )

        }

        if (oldIndex < amount) {

            return MoveResult(
                success = false,
                board = board,
                message = "Tiki cannot move up $amount position(s)."
            )

        }

        newBoard.removeAt(oldIndex)

        newBoard.add(oldIndex - amount, tiki)

        return MoveResult(
            success = true,
            board = newBoard
        )

    }

    // ==========================================================
    // TOPPLE
    // ==========================================================

    private fun tikiTopple(
        board: List<Int>,
        tiki: Int
    ): MoveResult {

        val newBoard = board.toMutableList()

        val index = newBoard.indexOf(tiki)

        if (index == -1) {

            return MoveResult(
                success = false,
                board = board,
                message = "Selected tiki not found."
            )

        }

        if (index == newBoard.lastIndex) {

            return MoveResult(
                success = false,
                board = board,
                message = "Selected tiki is already at the bottom."
            )

        }

        newBoard.removeAt(index)

        newBoard.add(tiki)

        return MoveResult(
            success = true,
            board = newBoard
        )

    }

    // ==========================================================
    // TOAST
    // ==========================================================

    private fun tikiToast(
        board: List<Int>
    ): MoveResult {

        if (board.isEmpty()) {

            return MoveResult(
                success = false,
                board = board,
                message = "Board is empty."
            )

        }

        val newBoard = board.toMutableList()

        newBoard.removeLast()

        return MoveResult(
            success = true,
            board = newBoard
        )

    }

}