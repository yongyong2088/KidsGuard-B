package com.kidsguard.app.data

/**
 * 应用分类状态。
 *
 * 这是整套拦截逻辑的核心：只有 RESTRICTED 才会触发弹窗。
 * LEARNING 连计时都不参与，UNKNOWN 只记录时长不弹窗。
 */
enum class AppCategory(val key: String, val label: String) {
    LEARNING("learning", "学习"),
    RESTRICTED("restricted", "受限"),
    UNKNOWN("unknown", "未分类");

    companion object {
        fun fromKey(key: String): AppCategory =
            entries.firstOrNull { it.key == key } ?: UNKNOWN
    }
}

/** 单个应用的归类规则 */
data class AppRule(
    val packageName: String,
    val category: AppCategory,
    /** 单独的触发阈值（分钟），0 表示跟随全局设置 */
    val thresholdOverride: Int = 0
)

/**
 * 一道题。
 *
 * @param index 在「模块 × 年级」600 题中的题号（0..599）。
 *              题目由 (模块, 年级, 题号) 确定性生成，所以题号就是题目的身份。
 * @param options 四选一的选项；为空表示需要键盘输入答案（数学填空题）
 */
data class Question(
    val id: String,
    val text: String,
    val answer: String,
    val subject: String,
    val grade: Int = 1,
    val index: Int = 0,
    val difficulty: Int = 0,
    val options: List<String> = emptyList(),
    val isCustom: Boolean = false
) {
    val hasOptions: Boolean get() = options.isNotEmpty()
}

/** 一次答题会话的状态（连续答对数、当前难度、统计数据） */
data class QuizState(
    val streak: Int = 0,
    val level: Int = 1,
    val correctCount: Int = 0,
    val wrongCount: Int = 0
)
