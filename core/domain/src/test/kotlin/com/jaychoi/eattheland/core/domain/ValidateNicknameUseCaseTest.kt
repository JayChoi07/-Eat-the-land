package com.jaychoi.eattheland.core.domain

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidateNicknameUseCaseTest {
    private val validate = ValidateNicknameUseCase()

    @Test
    fun `한글 영문 숫자 2~12자는 통과`() {
        assertTrue(validate("땅주인"))
        assertTrue(validate("ab"))
        assertTrue(validate("Walker2026"))
    }

    @Test
    fun `길이 위반과 특수문자 공백은 실패`() {
        assertFalse(validate("a"))
        assertFalse(validate("열세글자넘는닉네임입니다요"))
        assertFalse(validate("공백 있음"))
        assertFalse(validate("emoji🙂"))
        assertFalse(validate(""))
    }
}
