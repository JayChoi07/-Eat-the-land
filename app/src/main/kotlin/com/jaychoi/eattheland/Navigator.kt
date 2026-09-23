package com.jaychoi.eattheland

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey

/**
 * 백스택 조작 창구 (R-13-03). 화면 코드는 리스트를 직접 `add`·`removeLastOrNull` 하지 않고
 * 이 두 함수로만 이동한다. 상위 top-level 백스택이 여러 개 필요해지면 이 클래스만 바꾸면 된다.
 *
 * [navigate] 가 마지막 키와 같은 값을 다시 push 하지 않는 이유는, `NavDisplay` 의 `contentKey` 기본값이
 * `key.toString()` 합성이라 값이 같은 키가 둘 이상 쌓이면 엔트리를 구별하지 못하기 때문이다 (R-13-03).
 */
class Navigator(private val backStack: NavBackStack<NavKey>) {
    fun navigate(key: NavKey) {
        if (backStack.lastOrNull() != key) {
            backStack.add(key)
        }
    }

    fun goBack() {
        if (backStack.size > 1) {
            backStack.removeLastOrNull()
        }
    }

    /** 온보딩 완료처럼 되돌아갈 곳이 없어지는 전환. 백스택을 이 키 하나로 바꾼다. */
    fun replaceAll(key: NavKey) {
        backStack.clear()
        backStack.add(key)
    }
}
