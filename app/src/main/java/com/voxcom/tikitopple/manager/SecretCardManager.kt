package com.voxcom.tikitopple.manager

import com.voxcom.tikitopple.R
import com.voxcom.tikitopple.model.SecretCard

object SecretCardManager {

    // TODO:
    // Replace the target combinations below with the official Tiki Topple combinations.

    private val cards = listOf(

        SecretCard("S1", R.drawable.s1, listOf(1,2,3)),
        SecretCard("S2", R.drawable.s2, listOf(4,5,6)),
        SecretCard("S3", R.drawable.s3, listOf(7,8,9)),

        SecretCard("S4", R.drawable.s4, listOf(1,4,7)),
        SecretCard("S5", R.drawable.s5, listOf(2,5,8)),
        SecretCard("S6", R.drawable.s6, listOf(3,6,9)),

        SecretCard("S7", R.drawable.s7, listOf(1,5,9)),
        SecretCard("S8", R.drawable.s8, listOf(3,5,7)),
        SecretCard("S9", R.drawable.s9, listOf(2,4,9)),

        SecretCard("S10", R.drawable.s10, listOf(2,6,7)),
        SecretCard("S11", R.drawable.s11, listOf(3,4,8)),
        SecretCard("S12", R.drawable.s12, listOf(1,6,8)),

        SecretCard("S13", R.drawable.s13, listOf(2,6,8)),
        SecretCard("S14", R.drawable.s14, listOf(3,4,7)),
        SecretCard("S15", R.drawable.s15, listOf(1,5,8)),

        SecretCard("S16", R.drawable.s16, listOf(2,4,6)),
        SecretCard("S17", R.drawable.s17, listOf(3,5,9)),
        SecretCard("S18", R.drawable.s18, listOf(1,7,8)),

        SecretCard("S19", R.drawable.s19, listOf(2,5,7)),
        SecretCard("S20", R.drawable.s20, listOf(4,6,9)),
        SecretCard("S21", R.drawable.s21, listOf(1,3,8)),

        SecretCard("S22", R.drawable.s22, listOf(2,3,9)),
        SecretCard("S23", R.drawable.s23, listOf(4,5,8)),
        SecretCard("S24", R.drawable.s24, listOf(1,6,7)),

        SecretCard("S25", R.drawable.s25, listOf(3,7,9)),
        SecretCard("S26", R.drawable.s26, listOf(2,4,8)),
        SecretCard("S27", R.drawable.s27, listOf(1,5,6))
    )

    fun getCard(id: String): SecretCard =
        cards.first { it.id == id }

    fun getImage(id: String): Int =
        getCard(id).imageRes

    fun getTargets(id: String): List<Int> =
        getCard(id).targets

    fun assign(playerUids: List<String>): Map<String,String>{

        val shuffled = cards.shuffled()

        val assignments = mutableMapOf<String,String>()

        playerUids.forEachIndexed { index, uid ->
            assignments[uid] = shuffled[index].id
        }

        return assignments

    }

}