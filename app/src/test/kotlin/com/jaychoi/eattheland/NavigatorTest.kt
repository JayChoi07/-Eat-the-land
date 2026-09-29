package com.jaychoi.eattheland

import androidx.navigation3.runtime.NavBackStack
import androidx.navigation3.runtime.NavKey
import com.jaychoi.eattheland.feature.map.ui.MapKey
import com.jaychoi.eattheland.feature.onboarding.ui.OnboardingKey
import org.junit.Assert.assertEquals
import org.junit.Test

class NavigatorTest {
    @Test
    fun `온보딩이 백스택에 있으면 지도 하나로 바꾼다`() {
        val backStack = NavBackStack<NavKey>(OnboardingKey)
        Navigator(backStack).replaceAllIfPresent(from = OnboardingKey, to = MapKey)
        assertEquals(listOf<NavKey>(MapKey), backStack.toList())
    }

    @Test
    fun `온보딩이 백스택에 없으면 건드리지 않는다`() {
        val backStack = NavBackStack<NavKey>(MapKey)
        Navigator(backStack).replaceAllIfPresent(from = OnboardingKey, to = MapKey)
        assertEquals(listOf<NavKey>(MapKey), backStack.toList())
    }
}
