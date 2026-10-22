package com.dominikgold.tonka.data

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

interface AppClock {
    fun nowMillis(): Long
    fun today(): LocalDate
}

class SystemAppClock : AppClock {

    override fun nowMillis(): Long = Clock.System.now().toEpochMilliseconds()

    override fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
}
