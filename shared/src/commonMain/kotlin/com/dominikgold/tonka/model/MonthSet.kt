package com.dominikgold.tonka.model

import kotlinx.datetime.Month
import kotlinx.serialization.Serializable
import kotlin.jvm.JvmInline

// Stored as a 12-bit mask, bit 0 = January
@Serializable
@JvmInline
value class MonthSet(val mask: Int) {

    val isEmpty: Boolean get() = mask and ALL_MASK == 0

    val months: List<Month> get() = Month.entries.filter { contains(it) }

    operator fun contains(month: Month): Boolean = mask and bit(month) != 0

    fun with(month: Month) = MonthSet(mask or bit(month))

    fun without(month: Month) = MonthSet(mask and bit(month).inv())

    fun toggle(month: Month) = if (month in this) without(month) else with(month)

    operator fun plus(other: MonthSet) = MonthSet(mask or other.mask)

    fun label(): String {
        if (isEmpty) return ""
        if (mask and ALL_MASK == ALL_MASK) return "All year"
        val runs = runs()
        return runs.joinToString(", ") { (start, end) ->
            if (start == end) start.shortName() else "${start.shortName()}–${end.shortName()}"
        }
    }

    fun runs(): List<Pair<Month, Month>> {
        if (isEmpty) return emptyList()
        val indices = (0 until 12).filter { mask and (1 shl it) != 0 }
        val startIndex = (0 until 12).firstOrNull { mask and (1 shl it) == 0 }?.let { (it + 1) % 12 } ?: 0
        val ordered = (0 until 12).map { (startIndex + it) % 12 }.filter { it in indices }
        val result = mutableListOf<Pair<Int, Int>>()
        var runStart = ordered.first()
        var previous = runStart
        for (index in ordered.drop(1)) {
            if (index != (previous + 1) % 12) {
                result += runStart to previous
                runStart = index
            }
            previous = index
        }
        result += runStart to previous
        return result.map { (start, end) -> Month.entries[start] to Month.entries[end] }
    }

    companion object {
        private const val ALL_MASK = 0xFFF

        val None = MonthSet(0)
        val All = MonthSet(ALL_MASK)

        val Spring = range(Month.MARCH, Month.MAY)
        val Summer = range(Month.JUNE, Month.AUGUST)
        val Autumn = range(Month.SEPTEMBER, Month.NOVEMBER)
        val Winter = range(Month.DECEMBER, Month.FEBRUARY)

        fun of(vararg months: Month) = MonthSet(months.fold(0) { mask, month -> mask or bit(month) })

        fun of(months: Iterable<Month>) = MonthSet(months.fold(0) { mask, month -> mask or bit(month) })

        // Wraps around the year end, so NOVEMBER to FEBRUARY is four months
        fun range(start: Month, end: Month): MonthSet {
            var mask = 0
            var index = start.ordinal
            while (true) {
                mask = mask or (1 shl index)
                if (index == end.ordinal) break
                index = (index + 1) % 12
            }
            return MonthSet(mask)
        }

        private fun bit(month: Month) = 1 shl month.ordinal
    }
}

fun Month.shortName(): String = name.lowercase().replaceFirstChar { it.uppercase() }.take(3)

fun Month.displayName(): String = name.lowercase().replaceFirstChar { it.uppercase() }

fun Month.initial(): String = name.take(1)

fun Month.next(): Month = Month.entries[(ordinal + 1) % 12]

fun Month.previous(): Month = Month.entries[(ordinal + 11) % 12]
