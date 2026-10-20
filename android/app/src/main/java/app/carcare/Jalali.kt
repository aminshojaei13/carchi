package app.carcare

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale

private val FA_DIGITS = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
private val JALALI_MONTHS = arrayOf(
    "فروردین", "اردیبهشت", "خرداد", "تیر", "مرداد", "شهریور",
    "مهر", "آبان", "آذر", "دی", "بهمن", "اسفند",
)

fun String.persianDigits(): String = buildString {
    for (ch in this@persianDigits) append(if (ch in '0'..'9') FA_DIGITS[ch - '0'] else ch)
}

fun Int.fa(): String = NumberFormat.getIntegerInstance(Locale.US).format(this).persianDigits()

fun Long.fa(): String = this.toInt().fa()

/** متن فرم را به عدد تبدیل می‌کند؛ ارقام فارسی و عربی را هم می‌پذیرد. */
fun parseNumber(text: String): Int? {
    val fa = "۰۱۲۳۴۵۶۷۸۹"
    val ar = "٠١٢٣٤٥٦٧٨٩"
    val out = buildString {
        for (ch in text) {
            when {
                ch in '0'..'9' -> append(ch)
                fa.indexOf(ch) >= 0 -> append(fa.indexOf(ch))
                ar.indexOf(ch) >= 0 -> append(ar.indexOf(ch))
            }
        }
    }
    return out.toIntOrNull()
}

data class JalaliDate(val year: Int, val month: Int, val day: Int) {
    fun fa(): String = "${day.fa()} ${JALALI_MONTHS[month - 1]} ${year.fa()}"
}

fun localDate(ms: Long): LocalDate =
    Instant.ofEpochMilli(ms).atZone(ZoneId.systemDefault()).toLocalDate()

fun jalali(ms: Long): JalaliDate {
    val d = localDate(ms)
    return gregorianToJalali(d.year, d.monthValue, d.dayOfMonth)
}

fun gregorianToJalali(gy0: Int, gm: Int, gd: Int): JalaliDate {
    val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
    var jy = if (gy0 <= 1600) 0 else 979
    val gy = if (gy0 > 1600) gy0 - 1600 else gy0 - 621
    var days = 365 * gy + (gy + 3) / 4 - (gy + 99) / 100 + (gy + 399) / 400 - 80 +
        gd + gDaysInMonth[gm - 1]
    if (gm > 2 && (gy0 % 4 == 0 && gy0 % 100 != 0 || gy0 % 400 == 0)) days++
    jy += 33 * (days / 12053)
    days %= 12053
    jy += 4 * (days / 1461)
    days %= 1461
    if (days > 365) {
        jy += (days - 1) / 365
        days = (days - 1) % 365
    }
    val jm = if (days < 186) 1 + days / 31 else 7 + (days - 186) / 30
    val jd = 1 + if (days < 186) days % 31 else (days - 186) % 30
    return JalaliDate(jy, jm, jd)
}
