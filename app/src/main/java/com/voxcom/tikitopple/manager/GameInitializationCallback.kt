package com.voxcom.tikitopple.manager

interface GameInitializationCallback {

    fun onStatusChanged(message: String)

    fun onCompleted()

    fun onError(message: String)

}