package com.kidsguard.app.quiz

import com.kidsguard.app.data.PrefsManager
import com.kidsguard.app.data.Question
import com.kidsguard.app.data.QuestionRepository
import com.kidsguard.app.data.QuizState

/**
 * 答题引擎。
 *
 * 规则很简单、也最可靠：连续答对 [PrefsManager.passStreak] 题才算通过，中途答错计数归零。
 * 取题交给题库（[QuestionRepository.nextForLock]）——它按阶段决定难度档，
 * 弹窗阶段 1 用中等档、阶段 2 用困难档，避免孩子刷到超纲题。
 */
class QuizEngine(
    private val prefs: PrefsManager,
    private val repo: QuestionRepository
) {

    var state: QuizState = QuizState()
        private set

    /** 当前阶段：1 = 超出额度（中等档）、2 = 加密阶段（困难档） */
    var stage: Int = 1
        set(value) {
            field = value.coerceIn(1, 2)
        }

    private var current: Question? = null

    /** 取下一道题 */
    fun nextQuestion(): Question {
        val q = repo.nextForLock(prefs, stage)
        current = q
        return q
    }

    /** 当前题目的正确答案 */
    fun currentAnswer(): String = current?.answer.orEmpty()

    /** 当前题是否有选项（四选一） */
    fun hasOptions(): Boolean = current?.hasOptions ?: false

    fun currentOptions(): List<String> = current?.options ?: emptyList()

    /** 提交答案，返回是否正确 */
    fun submit(answer: String): Boolean {
        val correct = normalize(answer) == normalize(currentAnswer())
        state = if (correct) {
            state.copy(
                streak = state.streak + 1,
                correctCount = state.correctCount + 1
            )
        } else {
            state.copy(
                streak = 0,
                wrongCount = state.wrongCount + 1
            )
        }
        return correct
    }

    /** 是否已经达到解锁条件 */
    fun isPassed(): Boolean = state.streak >= prefs.passStreak

    /** 答案归一化：去空格、统一小写，避免「12 」和「apple 」被判错 */
    private fun normalize(s: String): String =
        s.trim().replace(" ", "").lowercase()
}
