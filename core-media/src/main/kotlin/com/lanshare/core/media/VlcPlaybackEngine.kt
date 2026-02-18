package com.lanshare.core.media

import uk.co.caprica.vlcj.factory.MediaPlayerFactory
import uk.co.caprica.vlcj.player.base.MediaPlayer

class VlcPlaybackEngine : AutoCloseable {
    private val mediaPlayerFactory: MediaPlayerFactory = MediaPlayerFactory()
    private val mediaPlayer: MediaPlayer = mediaPlayerFactory.mediaPlayers().newMediaPlayer()

    fun play(mediaUrl: String) {
        mediaPlayer.media().play(mediaUrl)
    }

    fun pause() {
        mediaPlayer.controls().setPause(true)
    }

    fun resume() {
        mediaPlayer.controls().setPause(false)
    }

    fun stop() {
        mediaPlayer.controls().stop()
    }

    fun seek(positionMillis: Long) {
        mediaPlayer.controls().setTime(positionMillis)
    }

    fun positionMillis(): Long = mediaPlayer.status().time()

    override fun close() {
        mediaPlayer.release()
        mediaPlayerFactory.release()
    }
}
