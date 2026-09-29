package com.jaychoi.eattheland

/** 두 번 눌러 종료(스펙 C §4). 첫 누름 뒤 [windowMillis] 안의 두 번째 누름만 종료다. 화면 상태라 ViewModel 이 아니다. */
class DoubleBackGate(private val windowMillis: Long = DEFAULT_WINDOW_MILLIS) {
    private var lastPressMillis: Long? = null

    /** 돌려주는 값이 true 면 종료한다. */
    fun press(nowMillis: Long): Boolean {
        val last = lastPressMillis
        val exit = last != null && nowMillis - last < windowMillis
        lastPressMillis = if (exit) null else nowMillis
        return exit
    }

    companion object {
        const val DEFAULT_WINDOW_MILLIS = 2_000L
    }
}
