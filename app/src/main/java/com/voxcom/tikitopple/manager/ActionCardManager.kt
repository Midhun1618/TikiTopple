package com.voxcom.tikitopple.manager

import com.voxcom.tikitopple.R
import com.voxcom.tikitopple.model.ActionCard

object ActionCardManager {

    private val defaultHand = listOf(

        ActionCard("UP1", R.drawable.a_1),
        ActionCard("UP2", R.drawable.a_2),
        ActionCard("UP3", R.drawable.a_3),

        ActionCard("TOPPLE1", R.drawable.a_t),
        ActionCard("TOPPLE2", R.drawable.a_t),

        ActionCard("TOAST", R.drawable.a_x)

    )

    /**
     * Every player receives the exact same cards
     * at the beginning of every round.
     */
    fun deal(): List<String> {

        return defaultHand.map { it.id }

    }

    fun removePlayedCard(
        cards: List<String>,
        playedCard: String
    ): List<String> {

        val remaining = cards.toMutableList()

        remaining.remove(playedCard)

        return remaining

    }

    fun hasCards(cards: List<String>): Boolean {

        return cards.isNotEmpty()

    }

    fun getImage(cardId: String): Int {

        return defaultHand.first {
            it.id == cardId
        }.imageRes

    }

    fun getCard(cardId: String): ActionCard {

        return defaultHand.first {
            it.id == cardId
        }

    }
}