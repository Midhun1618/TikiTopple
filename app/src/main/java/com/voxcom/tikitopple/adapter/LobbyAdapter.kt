package com.voxcom.tikitopple.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.voxcom.tikitopple.R
import com.voxcom.tikitopple.model.PlayerAvatars
import com.voxcom.tikitopple.model.LobbyPlayer

class LobbyAdapter(
    private val context: Context,
    private var players: MutableList<LobbyPlayer>
) : BaseAdapter() {

    override fun getCount(): Int = players.size

    override fun getItem(position: Int): Any = players[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(
        position: Int,
        convertView: View?,
        parent: ViewGroup?
    ): View {

        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.player_lobby, parent, false)

        val player = players[position]

        val readyImage = view.findViewById<ImageView>(R.id.isReady)
        val playerName = view.findViewById<TextView>(R.id.playerName)
        val hostText = view.findViewById<TextView>(R.id.isHost)

        playerName.text = player.name

        // NEW — fixed avatar per player, independent of list position
        readyImage.setImageResource(PlayerAvatars.RES[player.avatarIndex])

        if (player.host) {
            hostText.visibility = View.VISIBLE
        } else {
            hostText.visibility = View.GONE
        }

        if (player.ready) {

            readyImage.setBackgroundColor(
                ContextCompat.getColor(context, android.R.color.holo_green_light)
            )

        } else {

            readyImage.setBackgroundColor(
                ContextCompat.getColor(context, android.R.color.darker_gray)
            )

        }

        return view
    }

    fun updatePlayers(newPlayers: List<LobbyPlayer>) {

        players.clear()
        players.addAll(newPlayers)

        notifyDataSetChanged()

    }

}