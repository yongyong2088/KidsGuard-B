package com.kidsguard.app.quiz

/**
 * 英语单词 → 例句库。
 *
 * 原则：
 * - 每个例句 5~8 词，全部大写字母开头，可直接被 TTS 朗读
 * - 例句里一定包含目标单词（让小孩在语境中再听一次）
 * - 中文翻译口语化，避免书面语
 * - 收录范围：覆盖 [EnglishGenerator.GROUPS] 里全部 64 个基础词
 *
 * 用法：
 * ```
 *   EnglishExamples.lookup("apple")   // -> Pair("I have an apple.", "我有一个苹果。")
 *   EnglishExamples.lookup("apple")?.first?.let { EnglishSpeech.speak(it) }
 * ```
 *
 * 扩充方式：往 [EXAMPLES] 里加 `"word" to Pair("英文", "中文")` 即可，不需要改其他文件。
 */
object EnglishExamples {

    private val EXAMPLES: Map<String, Pair<String, String>> = mapOf(
        // 数字
        "one" to ("I have one book." to "我有一本书。"),
        "two" to ("I see two birds." to "我看见两只鸟。"),
        "three" to ("There are three apples." to "这里有三个苹果。"),
        "four" to ("I have four pens." to "我有四支钢笔。"),

        // 颜色
        "red" to ("The apple is red." to "这个苹果是红色的。"),
        "blue" to ("The sky is blue." to "天空是蓝色的。"),
        "green" to ("The grass is green." to "草地是绿色的。"),
        "yellow" to ("I like yellow flowers." to "我喜欢黄色的花。"),

        // 动物
        "cat" to ("I have a small cat." to "我有一只小猫。"),
        "dog" to ("The dog runs fast." to "小狗跑得很快。"),
        "bird" to ("The bird is in the tree." to "小鸟在树上。"),
        "fish" to ("I see a fish in water." to "我看见水里有一条鱼。"),

        // 文具
        "book" to ("This is my new book." to "这是我的新书。"),
        "pen" to ("I write with a pen." to "我用钢笔写字。"),
        "pencil" to ("I have a red pencil." to "我有一支红色的铅笔。"),
        "ruler" to ("I draw lines with a ruler." to "我用尺子画线。"),

        // 家庭
        "mother" to ("I love my mother." to "我爱我的妈妈。"),
        "father" to ("My father is tall." to "我爸爸很高。"),
        "sister" to ("She is my sister." to "她是我的姐姐。"),
        "brother" to ("My brother likes sports." to "我哥哥喜欢运动。"),

        // 水果
        "apple" to ("I have an apple." to "我有一个苹果。"),
        "banana" to ("I eat a banana." to "我吃了一根香蕉。"),
        "orange" to ("I like orange juice." to "我喜欢橙汁。"),
        "pear" to ("The pear is sweet." to "这个梨很甜。"),

        // 食物
        "rice" to ("I eat rice for lunch." to "我午饭吃米饭。"),
        "bread" to ("I have bread for breakfast." to "我早饭吃面包。"),
        "milk" to ("I drink milk every day." to "我每天都喝牛奶。"),
        "egg" to ("I eat one egg." to "我吃一个鸡蛋。"),

        // 身体
        "hand" to ("Wash your hand, please." to "请洗一下你的手。"),
        "head" to ("I have a small head." to "我有一个小脑袋。"),
        "eye" to ("I have two eyes." to "我有两只眼睛。"),
        "ear" to ("I hear with my ear." to "我用耳朵听。"),

        // 衣物
        "hat" to ("I have a new hat." to "我有一顶新帽子。"),
        "coat" to ("My coat is warm." to "我的外套很暖和。"),
        "shoe" to ("I wear new shoes." to "我穿着新鞋子。"),
        "sock" to ("I have white socks." to "我有白色的袜子。"),

        // 天气
        "sun" to ("The sun is bright." to "太阳很明亮。"),
        "rain" to ("I like rain in summer." to "我喜欢夏天的雨。"),
        "snow" to ("I see white snow." to "我看见白色的雪。"),
        "wind" to ("The wind is strong today." to "今天风很大。"),

        // 地点
        "school" to ("I go to school every day." to "我每天去上学。"),
        "park" to ("We play in the park." to "我们在公园里玩。"),
        "zoo" to ("I see a panda at the zoo." to "我在动物园看见熊猫。"),
        "shop" to ("Mom goes to the shop." to "妈妈去商店了。"),

        // 动作
        "run" to ("I can run very fast." to "我能跑得很快。"),
        "jump" to ("The frog can jump high." to "青蛙能跳得很高。"),
        "swim" to ("I like to swim in summer." to "我喜欢夏天游泳。"),
        "sing" to ("I sing a happy song." to "我唱一首开心的歌。"),

        // 学科
        "math" to ("I like math class." to "我喜欢数学课。"),
        "art" to ("I draw pictures in art class." to "我在美术课上画画。"),
        "music" to ("I love music very much." to "我非常喜欢音乐。"),
        "science" to ("Science is fun to learn." to "科学学起来很有趣。"),

        // 职业
        "teacher" to ("My teacher is kind." to "我的老师很和善。"),
        "doctor" to ("The doctor helps sick people." to "医生帮助生病的人。"),
        "driver" to ("My dad is a driver." to "我爸爸是一名司机。"),
        "farmer" to ("The farmer grows rice." to "农民种水稻。"),

        // 交通
        "bus" to ("I go to school by bus." to "我坐公共汽车去上学。"),
        "car" to ("Dad drives a red car." to "爸爸开一辆红色的车。"),
        "bike" to ("I ride my bike to the park." to "我骑自行车去公园。"),
        "train" to ("The train is very long." to "这列火车很长。"),

        // 形容词
        "big" to ("I see a big elephant." to "我看见一头大象。"),
        "small" to ("The small bird sings." to "小鸟在唱歌。"),
        "happy" to ("I am a happy boy." to "我是一个快乐的男孩。"),
        "tall" to ("The tall tree is green." to "高大的树是绿色的。")
    )

    /**
     * 查单词的例句。返回 Pair(英文, 中文)，找不到返回 null。
     * 查找不区分大小写，传入的 word 已经是题面 answer（一般是小写）。
     */
    fun lookup(word: String): Pair<String, String>? {
        if (word.isBlank()) return null
        return EXAMPLES[word.trim().lowercase()]
    }

    /** 当前库大小（调试用） */
    val size: Int get() = EXAMPLES.size
}