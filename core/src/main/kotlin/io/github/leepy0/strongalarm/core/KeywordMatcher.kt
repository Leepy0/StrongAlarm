package io.github.leepy0.strongalarm.core

object KeywordMatcher {
    /** 공백 제거 + 소문자 */
    fun normalize(s: String): String = s.filterNot { it.isWhitespace() }.lowercase()

    /** 제목에 포함된 첫 번째 키워드. 빈 키워드는 무시 (빈 문자열은 모든 제목에 매칭되므로) */
    fun firstHit(keywords: List<String>, title: String): String? {
        val t = normalize(title)
        return keywords.firstOrNull { k ->
            val n = normalize(k)
            n.isNotEmpty() && t.contains(n)
        }
    }
}
