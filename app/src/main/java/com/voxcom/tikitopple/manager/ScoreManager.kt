package com.voxcom.tikitopple.manager

object ScoreManager {

    /**
     * Calculates the score of a player for one round.
     *
     * secretTargets -> SecretCardManager.getTargets("S13")
     * board -> Current board after all moves
     */
    fun calculateScore(

        secretTargets: List<Int>,
        board: List<Int>

    ): Int {

        var score = 0

        secretTargets.forEach { tiki ->

            val position = board.indexOf(tiki)

            if (position != -1) {
                score += board.size - position
            }

        }

        return score

    }

    fun calculateAllScores(

        playerSecrets: Map<String, String>,
        board: List<Int>

    ): Map<String, Int> {

        val scores = mutableMapOf<String, Int>()

        playerSecrets.forEach { (uid, secretCard) ->

            val targets =
                SecretCardManager.getTargets(secretCard)

            scores[uid] = calculateScore(
                targets,
                board
            )

        }

        return scores

    }

}