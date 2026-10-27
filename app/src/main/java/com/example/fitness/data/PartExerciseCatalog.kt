package com.example.fitness.data

import android.content.Context
import java.util.Locale

/**
 * Single source of truth for "body part -> grouped exercise name list".
 *
 * Both:
 * - ui/TrainingPlanScreen (選擇訓練部位)
 * - coach/ui/CoachPlanCreateScreen (教練中心 -> Create Plan)
 *
 * must use this catalog so the lists stay in sync.
 */
object PartExerciseCatalog {

    /**
     * Key is a part id used across the app: chest/back/legs/arms/shoulders/abs
     * Value is a list of (groupTitle -> exerciseNameList)
     */
    fun groupsForPart(part: String): List<Pair<String, List<String>>> {
        return when (part) {
            "abs" -> ExerciseLibrary.AbdominalGroups.map { grp -> grp.name to grp.exercises.map { it.name } }
            "chest" -> chestGroups
            "back" -> backGroups
            "legs" -> legsGroups
            "arms" -> armsGroups
            "shoulders" -> shouldersGroups
            else -> emptyList()
        }
    }

    /**
     * 根据练习名称获取对应的身体部位
     */
    fun getPartForExercise(exerciseName: String): String? {
        val allParts = listOf("chest", "back", "legs", "arms", "shoulders", "abs")
        return allParts.firstOrNull { part ->
            groupsForPart(part).any { (_, exercises) ->
                exercises.any { it.equals(exerciseName, ignoreCase = true) }
            }
        }
    }

    /**
     * Get localized groups for a body part based on the current locale
     */
    fun getLocalizedGroupsForPart(part: String, context: Context): List<Pair<String, List<String>>> {
        val locale = context.resources.configuration.locales[0]
        val langCode = locale.language

        return when (part) {
            "abs" -> getLocalizedAbsGroups(langCode)
            "chest" -> getLocalizedChestGroups(langCode)
            "back" -> getLocalizedBackGroups(langCode)
            "legs" -> getLocalizedLegsGroups(langCode)
            "arms" -> getLocalizedArmsGroups(langCode)
            "shoulders" -> getLocalizedShouldersGroups(langCode)
            else -> emptyList()
        }
    }

    // IMPORTANT: Keep exercise names identical to what the UI expects and what gets stored.

    private val chestGroups: List<Pair<String, List<String>>> = listOf(
        "槓鈴/臥推" to listOf("槓鈴臥推 (Bench Press)", "窄握臥推 (窄握，偏三頭)"),
        "上斜" to listOf("上斜槓鈴臥推 (上胸)"),
        "下斜" to listOf("下斜槓鈴臥推 (下胸)"),
        "器械 - Cable" to listOf(
            "Cable 夾胸 (Cable Crossover)",
            "Cable 上胸夾胸 (Low to High)",
            "Cable 下胸夾胸 (High to Low)",
            "單手 Cable 夾胸 (改善左右胸不平衡)"
        ),
        "器械 - Machine" to listOf(
            "胸推機 (Chest Press)",
            "夾胸機 (Pec Deck Fly)",
            "上斜胸推機",
            "下斜胸推機",
            "俯臥推胸機 (Hammer Strength Type)"
        )
    )

    private val backGroups: List<Pair<String, List<String>>> = listOf(
        "背闊肌 (Latissimus)" to listOf(
            "引體向上（Pull-up）",
            "反手引體向上（Chin-up）",
            "雙槓垂拉（在雙槓上做反向划船）",
            "自重划船（Inverted Row）",
            "啞鈴單手划船（One-arm Dumbbell Row）",
            "槓鈴划船（Barbell Row）",
            "T-bar 划船（T-bar Row）",
            "槓鈴 Yates Row（反握）",
            "啞鈴上拉（Dumbbell Pullover — 胸背一起帶）",
            "Cable 下拉（Lat Pulldown）",
            "窄握下拉（Close Grip Pulldown）",
            "反握下拉（Reverse Grip Pulldown）",
            "Hammer Strength 背推拉機（Lat Row Machine）"
        ),
        "上背 (Rhomboids / Mid Traps)" to listOf(
            "自重倒划船（Inverted Row）",
            "超人式（Isometric Hold）",
            "雙手啞鈴划船（Dumbbell Row）",
            "槓鈴俯身划船（Pendlay Row）",
            "啞鈴後三角側平舉（Rear Delt Fly — 也會帶到上背）",
            "啞鈴反向飛鳥（Reverse Fly）",
            "坐姿划船（Seated Row） - 寬握/窄握/V-bar",
            "Cable 臉拉（Face Pull）",
            "Pec Deck 後飛鳥設定（Posterior Fly）"
        ),
        "下背 (Erector Spinae)" to listOf(
            "超人式（Superman）",
            "Bird Dog",
            "反向後伸（Back Extension）",
            "傳統硬舉（Deadlift）",
            "羅馬尼亞硬舉（RDL）",
            "Good Morning（啞鈴或槓鈴）",
            "壺鈴擺盪（Kettlebell Swing）"
        ),
        "斜方肌 (Trapezius)" to listOf(
            "聳肩（Shrug）",
            "啞鈴聳肩",
            "槓鈴聳肩",
            "Cable 聳肩",
            "坐姿划船（高角度）",
            "直立拉（Upright Row）"
        )
    )

    private val legsGroups: List<Pair<String, List<String>>> = listOf(
        "股四頭肌（大腿前側）" to listOf(
            "深蹲（Squat）",
            "前跨弓箭步（Forward Lunge）",
            "後跨弓箭步（Reverse Lunge）",
            "保加利亞分腿蹲（Bulgarian Split Squat）",
            "箱子登階（Step-up）",
            "靠牆靜蹲（Wall Sit）",
            "槓鈴深蹲（Barbell Back Squat）",
            "前蹲（Front Squat）",
            "上斜槓鈴深蹲（High-bar Squat）",
            "啞鈴深蹲（Dumbbell Squat）",
            "啞鈴分腿蹲/弓箭步",
            "45° 腿推機（Leg Press）",
            "腿部伸展（Leg Extension）",
            "哈克深蹲（Hack Squat）",
            "45° 史密斯深蹲（Smith Machine Squat）"
        ),
        "股二頭肌（大腿後側）" to listOf(
            "單腳早安（Single-leg Good Morning）",
            "仰臥腿屈（徒手版）",
            "硬舉（Deadlift）",
            "羅馬尼亞硬舉（RDL）",
            "直腿硬舉（Stiff Leg Deadlift）",
            "啞鈴早安（Good Morning）",
            "啞鈴後踢",
            "腿後側屈伸（Leg Curl）",
            "俯臥腿屈",
            "坐姿腿屈",
            "站姿腿屈"
        ),
        "臀部訓練（臀大肌/臀中肌）" to listOf(
            "臀推（Hip Thrust）",
            "臀橋（Glute Bridge）",
            "蚌式開合（Clam Shell）",
            "怪獸走路（Monster Walk）",
            "側抬腿（Side Leg Raise）",
            "槓鈴臀推（Barbell Hip Thrust）",
            "槓鈴臀橋",
            "啞鈴後踢（Kickback）",
            "啞鈴相撲深蹲（Sumo Squat）",
            "直腿硬舉（刺激臀腿）",
            "臀推機（Hip Thrust Machine）",
            "外展機（Hip Abductor）",
            "後踢機（Glute Kickback)"
        ),
        "內外側大腿（內收/外展）" to listOf(
            "外展機（Abductor）",
            "內收機（Adductor）",
            "Cable 外展 / 內收",
            "彈力帶側走（Lateral Band Walk）"
        ),
        "小腿（腓腸肌/比目魚肌）" to listOf(
            "站姿提踵（Standing Calf Raise）",
            "坐姿提踵（Seated Calf Raise）",
            "單腳提踵",
            "機械提踵（Calf Machine）",
            "Leg Press 上做提踵"
        )
    )

    private val armsGroups: List<Pair<String, List<String>>> = listOf(
        "二頭肌 (Biceps)" to listOf(
            "俯身靠牆屈臂拉（Bodyweight Bicep Curl）",
            "懸吊彎曲（Pull-up 反握可以帶二頭）",
            "站姿啞鈴彎舉（Dumbbell Curl）",
            "站姿槓鈴彎舉（Barbell Curl）",
            "槌式彎舉（Hammer Curl）",
            "交替啞鈴彎舉",
            "Zottman Curl（前段彎舉，後段旋轉手心向下放下）",
            "Cable 二頭肌彎舉（直立或坐姿）",
            "Preacher Curl 機器"
        ),
        "三頭肌 (Triceps)" to listOf(
            "窄距伏地挺身（Diamond Push-up）",
            "椅上三頭肌撐體（Bench Dip）",
            "臥姿啞鈴三頭肌伸展（Dumbbell Skull Crusher）",
            "槓鈴臥姿三頭肌伸展（Barbell Skull Crusher）",
            "單手啞鈴頭後伸展（Overhead Dumbbell Extension）",
            "啞鈴 Kickback",
            "Cable 下壓（Pushdown）",
            "繩索下壓（Rope Pushdown）",
            "Overhead Cable Extension",
            "機械三頭肌伸展"
        ),
        "前臂 (Forearm)" to listOf(
            "掌心向上/向下腕屈伸",
            "反手俯身懸吊握力",
            "手腕卷舉（Wrist Curl）",
            "反手手腕卷舉（Reverse Wrist Curl）",
            "槌式彎舉（Hammer Curl）",
            "Cable 手腕彎舉",
            "Cable 反手手腕彎舉"
        )
    )

    private val shouldersGroups: List<Pair<String, List<String>>> = listOf(
        "前三角肌（前肩）" to listOf(
            "伏地挺身（Push-up，重心前傾偏前肩）",
            "徒手肩推（Handstand Push-up，進階）",
            "啞鈴肩推（Dumbbell Overhead Press）",
            "槓鈴軍事推舉（Barbell Military Press）",
            "啞鈴前平舉（Front Raise）",
            "Cable 前平舉",
            "機械肩推（Shoulder Press Machine）"
        ),
        "側三角肌（中肩 / 側肩）" to listOf(
            "側平舉徒手（Hand support 側抬）",
            "彈力帶側平舉",
            "啞鈴側平舉（Lateral Raise）",
            "啞鈴俯身側平舉（Rear Lateral Raise）",
            "坐姿啞鈴側平舉（穩定核心）",
            "Cable 側平舉",
            "俯身 Cable 側平舉（Rear Lateral）"
        ),
        "後三角肌（後肩 / 上背側）" to listOf(
            "超人式（Superman，帶後肩+上背）",
            "桌下划船式（Inverted Row 變化，帶後肩）",
            "俯身後飛鳥（Bent-over Rear Delt Fly）",
            "坐姿後飛鳥（Seated Rear Delt Fly）",
            "Cable 反向飛鳥",
            "Pec Deck 反向飛鳥（Rear Delt Fly Machine）"
        )
    )

    private fun getLocalizedAbsGroups(langCode: String): List<Pair<String, List<String>>> {
        return when (langCode) {
            "en" -> listOf(
                "Upper Abs" to listOf("Crunch", "Sit-up", "Cable Crunch"),
                "Lower Abs" to listOf("Leg Raise", "Reverse Crunch", "Hanging Leg Raise"),
                "Obliques" to listOf("Russian Twist", "Side Plank", "Woodchop"),
                "Core" to listOf("Plank", "Dead Bug", "Mountain Climber")
            )
            "ja" -> listOf(
                "上腹部" to listOf("クランチ", "シットアップ", "ケーブルクランチ"),
                "下腹部" to listOf("レッグレイズ", "リバースクランチ", "ハンギングレッグレイズ"),
                "腹斜筋" to listOf("ロシアンツイスト", "サイドプランク", "ウッドチョップ"),
                "体幹" to listOf("プランク", "デッドバグ", "マウンテンクライマー")
            )
            "ko" -> listOf(
                "상복부" to listOf("크런치", "싯업", "케이블 크런치"),
                "하복부" to listOf("레그 레이즈", "리버스 크런치", "행잉 레그 레이즈"),
                "복사근" to listOf("러시안 트위스트", "사이드 플랭크", "우드찹"),
                "코어" to listOf("플랭크", "데드 버그", "마운틴 클라이머")
            )
            else -> ExerciseLibrary.AbdominalGroups.map { grp -> grp.name to grp.exercises.map { it.name } }
        }
    }

    private fun getLocalizedChestGroups(langCode: String): List<Pair<String, List<String>>> {
        return when (langCode) {
            "en" -> listOf(
                "Barbell/Bench Press" to listOf("Barbell Bench Press", "Close-Grip Bench Press"),
                "Incline" to listOf("Incline Barbell Press"),
                "Decline" to listOf("Decline Barbell Press"),
                "Cable" to listOf("Cable Crossover", "Low-to-High Cable Fly", "High-to-Low Cable Fly", "Single Arm Cable Fly"),
                "Machine" to listOf("Chest Press Machine", "Pec Deck Fly", "Incline Chest Press", "Decline Chest Press", "Hammer Strength Press")
            )
            "ja" -> listOf(
                "バーベル/ベンチプレス" to listOf("バーベルベンチプレス", "ナローグリップベンチプレス"),
                "インクライン" to listOf("インクラインバーベルプレス"),
                "デクライン" to listOf("デクラインバーベルプレス"),
                "ケーブル" to listOf("ケーブルクロスオーバー", "ローtoハイケーブルフライ", "ハイtoローケーブルフライ", "ワンアームケーブルフライ"),
                "マシン" to listOf("チェストプレス", "ペックデックフライ", "インクラインチェストプレス", "デクラインチェストプレス", "ハンマーストレングスプレス")
            )
            "ko" -> listOf(
                "바벨/벤치프레스" to listOf("바벨 벤치프레스", "클로즈그립 벤치프레스"),
                "인클라인" to listOf("인클라인 바벨프레스"),
                "디클라인" to listOf("디클라인 바벨프레스"),
                "케이블" to listOf("케이블 크로스오버", "로우투하이 케이블 플라이", "하이투로우 케이블 플라이", "원암 케이블 플라이"),
                "머신" to listOf("체스트 프레스 머신", "펙덱 플라이", "인클라인 체스트 프레스", "디클라인 체스트 프레스", "해머 스트렝스 프레스")
            )
            else -> chestGroups
        }
    }

    private fun getLocalizedBackGroups(langCode: String): List<Pair<String, List<String>>> {
        return when (langCode) {
            "en" -> listOf(
                "Lats (Latissimus)" to listOf("Pull-up", "Chin-up", "Dip Pull", "Inverted Row", "One-arm Dumbbell Row", "Barbell Row", "T-bar Row", "Yates Row", "Dumbbell Pullover", "Lat Pulldown", "Close Grip Pulldown", "Reverse Grip Pulldown"),
                "Upper Back (Rhomboids)" to listOf("Inverted Row", "Superman Hold", "Dumbbell Row", "Pendlay Row", "Rear Delt Fly", "Reverse Fly", "Seated Row", "Face Pull", "Posterior Fly"),
                "Lower Back (Erector)" to listOf("Superman", "Bird Dog", "Back Extension", "Deadlift", "Romanian Deadlift", "Good Morning", "Kettlebell Swing"),
                "Trapezius" to listOf("Shrug", "Dumbbell Shrug", "Barbell Shrug", "Cable Shrug", "High Seated Row", "Upright Row")
            )
            "ja" -> listOf(
                "広背筋" to listOf("プルアップ", "チンアップ", "ディップスプル", "インバーテッドロウ", "ワンアームダンベルロウ", "バーベルロウ", "Tバーロウ", "イエーツロウ", "ダンベルプルオーバー", "ラットプルダウン", "ナローグリッププルダウン", "リバースグリッププルダウン"),
                "上背部" to listOf("インバーテッドロウ", "スーパーマンホールド", "ダンベルロウ", "ペンドレイロウ", "リアデルトフライ", "リバースフライ", "シーテッドロウ", "フェイスプル", "ポステリアフライ"),
                "下背部" to listOf("スーパーマン", "バードドッグ", "バックエクステンション", "デッドリフト", "ルーマニアンデッドリフト", "グッドモーニング", "ケトルベルスイング"),
                "僧帽筋" to listOf("シュラッグ", "ダンベルシュラッグ", "バーベルシュラッグ", "ケーブルシュラッグ", "ハイシーテッドロウ", "アップライトロウ")
            )
            "ko" -> listOf(
                "광배근" to listOf("풀업", "친업", "딥 풀", "인버티드 로우", "원암 덤벨 로우", "바벨 로우", "T바 로우", "예이츠 로우", "덤벨 풀오버", "랫 풀다운", "클로즈 그립 풀다운", "리버스 그립 풀다운"),
                "상부 등" to listOf("인버티드 로우", "슈퍼맨 홀드", "덤벨 로우", "펜들레이 로우", "리어 델트 플라이", "리버스 플라이", "시티드 로우", "페이스 풀", "포스테리어 플라이"),
                "하부 등" to listOf("슈퍼맨", "버드독", "백 익스텐션", "데드리프트", "루마니안 데드리프트", "굿모닝", "케틀벨 스윙"),
                "승모근" to listOf("슈러그", "덤벨 슈러그", "바벨 슈러그", "케이블 슈러그", "하이 시티드 로우", "업라이트 로우")
            )
            else -> backGroups
        }
    }

    private fun getLocalizedLegsGroups(langCode: String): List<Pair<String, List<String>>> {
        return when (langCode) {
            "en" -> listOf(
                "Quadriceps" to listOf("Squat", "Forward Lunge", "Reverse Lunge", "Bulgarian Split Squat", "Step-up", "Wall Sit", "Barbell Back Squat", "Front Squat", "Dumbbell Squat", "Leg Press", "Leg Extension", "Hack Squat"),
                "Hamstrings" to listOf("Single-leg Good Morning", "Lying Leg Curl", "Deadlift", "Romanian Deadlift", "Stiff Leg Deadlift", "Good Morning", "Seated Leg Curl", "Standing Leg Curl"),
                "Glutes" to listOf("Hip Thrust", "Glute Bridge", "Clam Shell", "Monster Walk", "Side Leg Raise", "Barbell Hip Thrust", "Dumbbell Kickback", "Sumo Squat", "Hip Abductor", "Glute Kickback Machine"),
                "Inner/Outer Thigh" to listOf("Hip Abductor", "Hip Adductor", "Cable Abduction/Adduction", "Lateral Band Walk"),
                "Calves" to listOf("Standing Calf Raise", "Seated Calf Raise", "Single Leg Calf Raise", "Calf Machine", "Leg Press Calf Raise")
            )
            "ja" -> listOf(
                "大腿四頭筋" to listOf("スクワット", "フォワードランジ", "リバースランジ", "ブルガリアンスプリットスクワット", "ステップアップ", "ウォールシット", "バーベルバックスクワット", "フロントスクワット", "ダンベルスクワット", "レッグプレス", "レッグエクステンション", "ハックスクワット"),
                "ハムストリングス" to listOf("シングルレググッドモーニング", "ライイングレッグカール", "デッドリフト", "ルーマニアンデッドリフト", "スティフレッグデッドリフト", "グッドモーニング", "シーテッドレッグカール", "スタンディングレッグカール"),
                "臀筋" to listOf("ヒップスラスト", "グルートブリッジ", "クラムシェル", "モンスターウォーク", "サイドレッグレイズ", "バーベルヒップスラスト", "ダンベルキックバック", "スモウスクワット", "ヒップアブダクター", "グルートキックバックマシン"),
                "内転筋/外転筋" to listOf("ヒップアブダクター", "ヒップアダクター", "ケーブル外転/内転", "ラテラルバンドウォーク"),
                "ふくらはぎ" to listOf("スタンディングカーフレイズ", "シーテッドカーフレイズ", "シングルレッグカーフレイズ", "カーフマシン", "レッグプレスカーフレイズ")
            )
            "ko" -> listOf(
                "대퇴사두근" to listOf("스쿼트", "포워드 런지", "리버스 런지", "불가리안 스플릿 스쿼트", "스텝업", "월 싯", "바벨 백 스쿼트", "프론트 스쿼트", "덤벨 스쿼트", "레그 프레스", "레그 익스텐션", "핵 스쿼트"),
                "햄스트링" to listOf("싱글레그 굿모닝", "라잉 레그 컬", "데드리프트", "루마니안 데드리프트", "스티프 레그 데드리프트", "굿모닝", "시티드 레그 컬", "스탠딩 레그 컬"),
                "둔근" to listOf("힙 스러스트", "글루트 브릿지", "클램쉘", "몬스터 워크", "사이드 레그 레이즈", "바벨 힙 스러스트", "덤벨 킥백", "스모 스쿼트", "힙 어브덕터", "글루트 킥백 머신"),
                "내전근/외전근" to listOf("힙 어브덕터", "힙 어덕터", "케이블 외전/내전", "래터럴 밴드 워크"),
                "종아리" to listOf("스탠딩 카프 레이즈", "시티드 카프 레이즈", "싱글 레그 카프 레이즈", "카프 머신", "레그 프레스 카프 레이즈")
            )
            else -> legsGroups
        }
    }

    private fun getLocalizedArmsGroups(langCode: String): List<Pair<String, List<String>>> {
        return when (langCode) {
            "en" -> listOf(
                "Biceps" to listOf("Bodyweight Bicep Curl", "Chin-up", "Dumbbell Curl", "Barbell Curl", "Hammer Curl", "Alternating Dumbbell Curl", "Zottman Curl", "Cable Bicep Curl", "Preacher Curl"),
                "Triceps" to listOf("Diamond Push-up", "Bench Dip", "Dumbbell Skull Crusher", "Barbell Skull Crusher", "Overhead Dumbbell Extension", "Dumbbell Kickback", "Cable Pushdown", "Rope Pushdown", "Overhead Cable Extension"),
                "Forearm" to listOf("Wrist Curl", "Reverse Wrist Curl", "Hammer Curl", "Cable Wrist Curl", "Reverse Cable Wrist Curl")
            )
            "ja" -> listOf(
                "二頭筋" to listOf("自重バイセップカール", "チンアップ", "ダンベルカール", "バーベルカール", "ハンマーカール", "オルタネイトダンベルカール", "ゾットマンカール", "ケーブルバイセップカール", "プリーチャーカール"),
                "三頭筋" to listOf("ダイヤモンドプッシュアップ", "ベンチディップ", "ダンベルスカルクラッシャー", "バーベルスカルクラッシャー", "オーバーヘッドダンベルエクステンション", "ダンベルキックバック", "ケーブルプッシュダウン", "ロープウッシュダウン", "オーバーヘッドケーブルエクステンション"),
                "前腕" to listOf("リストカール", "リバースリストカール", "ハンマーカール", "ケーブルリストカール", "リバースケーブルリストカール")
            )
            "ko" -> listOf(
                "이두근" to listOf("바디웨이트 바이셉 컬", "친업", "덤벨 컬", "바벨 컬", "해머 컬", "얼터네이팅 덤벨 컬", "조트만 컬", "케이블 바이셉 컬", "프리처 컬"),
                "삼두근" to listOf("다이아몬드 푸시업", "벤치 딥", "덤벨 스컬 크러셔", "바벨 스컬 크러셔", "오버헤드 덤벨 익스텐션", "덤벨 킥백", "케이블 푸시다운", "로프 푸시다운", "오버헤드 케이블 익스텐션"),
                "전완근" to listOf("리스트 컬", "리버스 리스트 컬", "해머 컬", "케이블 리스트 컬", "리버스 케이블 리스트 컬")
            )
            else -> armsGroups
        }
    }

    private fun getLocalizedShouldersGroups(langCode: String): List<Pair<String, List<String>>> {
        return when (langCode) {
            "en" -> listOf(
                "Front Deltoid" to listOf("Push-up", "Handstand Push-up", "Dumbbell Overhead Press", "Barbell Military Press", "Front Raise", "Cable Front Raise", "Shoulder Press Machine"),
                "Side Deltoid" to listOf("Lateral Raise", "Band Lateral Raise", "Dumbbell Lateral Raise", "Seated Lateral Raise", "Cable Lateral Raise"),
                "Rear Deltoid" to listOf("Superman", "Inverted Row Variation", "Bent-over Rear Delt Fly", "Seated Rear Delt Fly", "Cable Reverse Fly", "Rear Delt Fly Machine")
            )
            "ja" -> listOf(
                "前部三角筋" to listOf("プッシュアップ", "ハンドスタンドプッシュアップ", "ダンベルオーバーヘッドプレス", "バーベルミリタリープレス", "フロントレイズ", "ケーブルフロントレイズ", "ショルダープレスマシン"),
                "側部三角筋" to listOf("ラテラルレイズ", "バンドラテラルレイズ", "ダンベルラテラルレイズ", "シーテッドラテラルレイズ", "ケーブルラテラルレイズ"),
                "後部三角筋" to listOf("スーパーマン", "インバーテッドロウバリエーション", "ベントオーバーリアデルトフライ", "シーテッドリアデルトフライ", "ケーブルリバースフライ", "リアデルトフライマシン")
            )
            "ko" -> listOf(
                "전면 삼각근" to listOf("푸시업", "핸드스탠드 푸시업", "덤벨 오버헤드 프레스", "바벨 밀리터리 프레스", "프론트 레이즈", "케이블 프론트 레이즈", "숄더 프레스 머신"),
                "측면 삼각근" to listOf("래터럴 레이즈", "밴드 래터럴 레이즈", "덤벨 래터럴 레이즈", "시티드 래터럴 레이즈", "케이블 래터럴 레이즈"),
                "후면 삼각근" to listOf("슈퍼맨", "인버티드 로우 변형", "벤트오버 리어 델트 플라이", "시티드 리어 델트 플라이", "케이블 리버스 플라이", "리어 델트 플라이 머신")
            )
            else -> shouldersGroups
        }
    }
}
