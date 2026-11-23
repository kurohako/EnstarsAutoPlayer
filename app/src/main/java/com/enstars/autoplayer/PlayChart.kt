package com.enstars.autoplayer

/** All times are song/video relative in milliseconds; lanes are 0..8, half lanes allowed. */
enum class NoteKind {
 TAP, FLICK_LEFT, FLICK_RIGHT, FLICK_UP, FLICK_DOWN, HOLD, SLIDE;

 val isSustained: Boolean get() = this == HOLD || this == SLIDE
}

data class ChartWaypoint(val timeMs: Long, val lane: Float)

data class PlayNote(
 val timeMs: Long,
 val lane: Float,
 val kind: NoteKind,
 val endTimeMs: Long = timeMs,
 val path: List<ChartWaypoint> = emptyList(),
 val confidence: String = "source",
 val releaseKind: NoteKind? = null
)
