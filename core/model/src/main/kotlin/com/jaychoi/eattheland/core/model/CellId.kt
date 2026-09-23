package com.jaychoi.eattheland.core.model

/** H3 셀 주소(16진 문자열). Firestore `cells` 문서 ID와 같다. */
@JvmInline
value class CellId(val value: String)
