package com.example.paperball.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool

class SoundManager(context: Context) {
    private var soundPool: SoundPool? = null
    private var swooshSound: Int = 0
    private var scoreSound: Int = 0
    private var bounceSound: Int = 0
    private var missSound: Int = 0
    
    var isEnabled = true
    
    init {
        val audioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_GAME)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        
        soundPool = SoundPool.Builder()
            .setMaxStreams(5)
            .setAudioAttributes(audioAttributes)
            .build()
        
        // Note: In a real app, you'd load actual sound files from res/raw
        // For now, these are placeholders
    }
    
    fun playSwoosh() {
        if (isEnabled && swooshSound != 0) {
            soundPool?.play(swooshSound, 0.5f, 0.5f, 1, 0, 1.0f)
        }
    }
    
    fun playScore() {
        if (isEnabled && scoreSound != 0) {
            soundPool?.play(scoreSound, 1.0f, 1.0f, 1, 0, 1.0f)
        }
    }
    
    fun playBounce() {
        if (isEnabled && bounceSound != 0) {
            soundPool?.play(bounceSound, 0.3f, 0.3f, 1, 0, 1.0f)
        }
    }
    
    fun playMiss() {
        if (isEnabled && missSound != 0) {
            soundPool?.play(missSound, 0.7f, 0.7f, 1, 0, 1.0f)
        }
    }
    
    fun release() {
        soundPool?.release()
        soundPool = null
    }
}
