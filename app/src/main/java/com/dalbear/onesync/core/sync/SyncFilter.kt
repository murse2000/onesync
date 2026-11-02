package com.dalbear.onesync.core.sync

/**
 * 이름 기반 제외 규칙.
 *
 * 이름 패턴은 원본 앱과 같이 "전체 경로"가 아니라 "파일/폴더 이름"에 적용한다.
 * 포함 패턴은 파일에만 적용한다. 폴더는 항상 순회해야 하위 파일을 판단할 수 있기 때문이다.
 */
data class FilterOptions(
    val excludeHidden: Boolean = false,
    /** 하위 폴더를 동기화하지 않고 최상위 항목만 다룬다. */
    val onlyTopLevel: Boolean = false,
    val includePatterns: List<String> = emptyList(),
    val excludePatterns: List<String> = emptyList(),
)

class SyncFilter(private val options: FilterOptions = FilterOptions()) {

    fun isExcluded(path: String, isDirectory: Boolean): Boolean {
        val name = path.substringAfterLast('/')
        if (options.onlyTopLevel && path.contains('/')) return true
        if (options.excludeHidden && name.startsWith(".")) return true
        if (options.excludePatterns.any { matches(it, name) }) return true
        if (!isDirectory && options.includePatterns.isNotEmpty()) {
            return options.includePatterns.none { matches(it, name) }
        }
        return false
    }

    companion object {
        private val INVALID_CHARS = charArrayOf('\\', '/', ':', '*', '?', '"', '<', '>', '|')

        /**
         * 원격 이름을 안드로이드 파일 시스템에 그대로 쓸 수 있는지 검사한다.
         * 쓸 수 없으면 사용자에게 보여줄 사유를, 쓸 수 있으면 null을 돌려준다.
         */
        fun invalidLocalNameReason(name: String): String? {
            if (name.isEmpty()) return "빈 이름"
            if (name.any { it in INVALID_CHARS }) return "안드로이드에서 쓸 수 없는 문자 포함"
            if (name.any { it.code in 0..31 }) return "제어 문자 포함"
            if (name.endsWith(".")) return "마침표로 끝남"
            if (name.endsWith(" ")) return "공백으로 끝남"
            return null
        }

        /** '*'는 임의 길이, '?'는 한 글자. 대소문자는 구분하지 않는다. */
        private fun matches(pattern: String, name: String): Boolean {
            val regex = buildString {
                append('^')
                for (ch in pattern) {
                    when (ch) {
                        '*' -> append(".*")
                        '?' -> append('.')
                        else -> append(Regex.escape(ch.toString()))
                    }
                }
                append('$')
            }
            return Regex(regex, RegexOption.IGNORE_CASE).matches(name)
        }
    }
}
