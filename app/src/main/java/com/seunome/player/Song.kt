package com.seunome.player

data class Song(
    val id: Long,
    val uri: String,
    val title: String,
    val artist: String,
    val duration: Long
)
