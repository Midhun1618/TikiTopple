package com.voxcom.tikitopple.utils

object PlayerNameGenerator {

    private val prefixes = listOf(
        "Cute","Little","Angry","Hungry","Happy","Sleepy","Crazy","Tiny","Brave","Fast","Lucky","Shiny","Magic","Wild","Sneaky","Funny","Smart","Royal","Mighty","Gentle","Fluffy","Chilly","Bouncy","Silent","Swift"
    )

    private val names = listOf(
        "Cat","Dog","Bee","Duck","Fox","Wolf","Tiger","Lion","Bear","Panda","Rabbit","Owl","Hippo","Donkey","Parrot","Eagle","Dolphin","Shark","Penguin","Frog","Monkey","Elephant","Turtle","Whale","Deer"
    )

    fun generate(): String {

        val prefix = prefixes.random()
        val animal = names.random()
        val postfix = (0..99).random().toString().padStart(2, '0')

        return "$prefix$animal$postfix"
    }
}