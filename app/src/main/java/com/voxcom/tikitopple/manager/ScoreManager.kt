package com.voxcom.tikitopple.manager

object ScoreManager {

    fun calculateScore(
        secretTargets: List<Int>,
        board: List<Int>
    ): Int {

        var score = 0

        if (secretTargets.size != 3) return 0

        val first = board.indexOf(secretTargets[0])
        val second = board.indexOf(secretTargets[1])
        val third = board.indexOf(secretTargets[2])

        // First target -> must be 1st
        if (first == 0) {
            score += 9
        }

        // Second target -> must be 1st or 2nd
        if (second in 0..1) {
            score += 5
        }

        // Third target -> must be 1st, 2nd or 3rd
        if (third in 0..2) {
            score += 2
        }

        return score
    }

    fun calculateAllScores(
        playerSecrets: Map<String, String>,
        board: List<Int>
    ): Map<String, Int> {

        val scores = mutableMapOf<String, Int>()

        playerSecrets.forEach { (uid, secretCard) ->

            val targets = SecretCardManager.getTargets(secretCard)

            scores[uid] = calculateScore(targets, board)
        }

        return scores
    }
}