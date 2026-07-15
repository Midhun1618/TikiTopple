package com.voxcom.tikitopple

import android.content.Intent
import android.os.Bundle
import android.view.animation.AnimationUtils
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.database.FirebaseDatabase
import com.voxcom.tikitopple.utils.PlayerNameGenerator

class SplashActivity : AppCompatActivity() {

    private lateinit var auth: FirebaseAuth
    private lateinit var loader1 : ImageView
    private lateinit var loader2 : ImageView
    private val database = FirebaseDatabase.getInstance().reference

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_splash)

        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }
        loader1 = findViewById(R.id.rotating_splash)
        loader2 = findViewById(R.id.anti_rotating_splash)

        val rotateR = AnimationUtils.loadAnimation(this, R.anim.rotater)
        val rotateL = AnimationUtils.loadAnimation(this, R.anim.anti_rotator)

        loader1.startAnimation(rotateR)
        loader2.startAnimation(rotateL)

        initializeFirebase()
    }
    private fun initializeFirebase() {
        auth = FirebaseAuth.getInstance()

        if (auth.currentUser != null) {
            setPlayerOnline()
        } else {
            anonymousLogin()
        }
    }

    private fun anonymousLogin() {
        auth.signInAnonymously()
            .addOnSuccessListener {
                createPlayerIfNeeded()
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Authentication Failed",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }
    private fun createPlayerIfNeeded() {

        val uid = auth.currentUser!!.uid
        val playerRef = database.child("players").child(uid)

        playerRef.get().addOnSuccessListener { snapshot ->

            if (!snapshot.exists()) {

                val player = hashMapOf(
                    "uid" to uid,
                    "name" to PlayerNameGenerator.generate(),
                    "online" to true,
                    "createdAt" to System.currentTimeMillis()
                )

                playerRef.setValue(player)
                    .addOnSuccessListener {
                        setPlayerOnline()
                    }

            } else {
                setPlayerOnline()
            }
        }
    }

    private fun setPlayerOnline() {

        val uid = auth.currentUser!!.uid
        val playerRef = database.child("players").child(uid)

        playerRef.child("online").setValue(true)
            .addOnSuccessListener {
                registerPresence()
            }
            .addOnFailureListener {
                Toast.makeText(
                    this,
                    "Failed to update online status",
                    Toast.LENGTH_SHORT
                ).show()
            }
    }

    private fun registerPresence() {

        val uid = auth.currentUser!!.uid
        val playerRef = database.child("players").child(uid)

        playerRef.child("online")
            .onDisconnect()
            .setValue(false)

        navigateToHome()
    }

    private fun navigateToHome() {
        startActivity(Intent(this, HomeActivity::class.java))
        finish()
    }

}