package com.kidsguard.app.quiz

import com.kidsguard.app.data.Difficulty
import com.kidsguard.app.data.SportTask
import kotlin.random.Random

/**
 * 运动打卡任务库。
 *
 * 运动模块不做纸笔题——「跳绳标准是多少」这种选择题学不到任何东西。
 * 这里改成真实动作任务，孩子照着做完后点「完成了」，家长端能看到记录。
 *
 * 10 种动作 × 3 个难度档 × 3 个年级 = 90 种参数组合，铺满 600 个任务位。
 * 目标数量随年级和难度递增，动作要领写在 tip 里。
 */
object SportTaskBank {

    fun taskFor(grade: Int, index: Int): SportTask {
        val def = ACTIONS[index % ACTIONS.size]
        val d = Difficulty.of(index)
        val target = def.targets[grade.coerceIn(1, 3) - 1][d.idx]
        return SportTask(
            id = "sport_${grade}_$index",
            name = def.name,
            desc = def.desc(target),
            target = target,
            unit = def.unit,
            tip = def.tip
        )
    }

    /** 随机取一个任务，用于弹窗时派发（避免每次都是同一项） */
    fun randomTask(grade: Int, seed: Int): SportTask {
        val index = Random(seed).nextInt(600)
        return taskFor(grade, index)
    }

    private class Action(
        val name: String,
        val unit: String,
        /** [年级][难度档] → 目标数量 */
        val targets: List<List<Int>>,
        val desc: (Int) -> String,
        val tip: String
    )

    private val ACTIONS = listOf(
        Action("跳绳", "个",
            listOf(listOf(30, 60, 100), listOf(50, 90, 140), listOf(70, 120, 180)),
            { "连续跳绳 $it 个" },
            "手腕摇绳，脚尖轻点地，膝盖微屈缓冲"),
        Action("拍球", "下",
            listOf(listOf(20, 40, 60), listOf(30, 50, 80), listOf(40, 70, 100)),
            { "原地拍球 $it 下" },
            "手指张开控球，肘部带动手腕，眼睛看前方不看球"),
        Action("深蹲", "个",
            listOf(listOf(10, 15, 20), listOf(12, 18, 25), listOf(15, 22, 30)),
            { "标准深蹲 $it 个" },
            "双脚与肩同宽，腰背挺直，蹲到大腿与地面平行"),
        Action("开合跳", "个",
            listOf(listOf(15, 25, 40), listOf(20, 35, 50), listOf(25, 45, 65)),
            { "开合跳 $it 个" },
            "跳起时双脚分开、双手举过头顶，落地时并拢"),
        Action("高抬腿", "个",
            listOf(listOf(20, 30, 50), listOf(25, 40, 60), listOf(30, 50, 80)),
            { "原地高抬腿 $it 个" },
            "大腿抬到与地面平行，前脚掌着地，节奏均匀"),
        Action("平板支撑", "秒",
            listOf(listOf(15, 25, 40), listOf(20, 35, 50), listOf(30, 45, 70)),
            { "平板支撑 $it 秒" },
            "身体成一条直线，收紧腹部，不要塌腰或翘臀"),
        Action("单脚站立", "秒",
            listOf(listOf(10, 20, 30), listOf(15, 25, 40), listOf(20, 35, 50)),
            { "单脚站立 $it 秒（左右脚各一次）" },
            "脚踩实地面，膝盖微屈，双手可侧平举保持平衡"),
        Action("蛙跳", "个",
            listOf(listOf(8, 12, 18), listOf(10, 15, 22), listOf(12, 18, 26)),
            { "蛙跳 $it 个" },
            "半蹲起跳，落地时前脚掌先着地并缓冲"),
        Action("拉伸放松", "秒",
            listOf(listOf(20, 30, 45), listOf(25, 35, 50), listOf(30, 45, 60)),
            { "拉伸放松 $it 秒" },
            "重点拉伸大腿后侧和小腿，动作缓慢不弹动"),
        Action("原地小跑", "秒",
            listOf(listOf(30, 60, 90), listOf(40, 70, 110), listOf(50, 90, 130)),
            { "原地小跑 $it 秒" },
            "前脚掌着地，手臂自然摆动，呼吸保持均匀")
    )
}
