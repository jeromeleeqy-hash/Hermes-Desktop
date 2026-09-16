package com.qingyu.hermescompanion.desktop

import androidx.compose.runtime.*
import java.util.Locale
import org.json.JSONObject
import com.qingyu.hermescompanion.platform.DesktopHost

private var uiLanguage by mutableStateOf("zh")
private val englishLabels by lazy { JSONObject(object {}.javaClass.getResourceAsStream("/desktop-en.json")?.bufferedReader()?.use { it.readText() } ?: "{}") }
fun setDesktopLanguage(value:String) {
    uiLanguage=if(value=="en" || (value=="system" && !Locale.getDefault().language.startsWith("zh")))"en" else "zh"
    com.qingyu.hermescompanion.i18n.desktopLanguage=uiLanguage
}
private val shortcutLabels = setOf(
    "⌘ N 新对话 · ⌘ K 搜索 · ⌘ P 命令 · ⌘ S 保存 · ⌘ Enter 发送 · ⌘ , 设置",
    "把图片或文件拖入窗口，可以添加到当前对话。Enter 换行，⌘ Enter 发送。",
    "新对话 · ⌘ N", "设置 · ⌘ ,", "搜索 · ⌘ K", "快捷命令 · ⌘ P", "⌘ Enter 发送", "发送 · ⌘ Enter",
)
fun tr(text:String):String {
    val label = if(uiLanguage=="en") englishLabels.optString(text,text) else text
    return if(text in shortcutLabels)DesktopHost.shortcutLabel(label)else label
}
fun desktopLocale():Locale=if(uiLanguage=="en")Locale.ENGLISH else Locale.CHINA

internal fun councilPrompt(text:String,mode:String):String = when(mode) {
    "quick" -> """
        [Hermes Desktop · 快速会审]
        当前会话使用 MoA。比较参考模型真实、独立的返回结果，不虚构专家对话，不重复原始分析。
        最终输出：会审结论、共识、关键分歧与裁决、证据与风险、相比单模型的增益、置信度与未决事项。
        [原始问题]
        $text
    """.trimIndent()
    "deep" -> """
        [Hermes Desktop · 深度专家会审]
        先判断是否值得会审；简单问题直接回答并说明无需会审。
        需要会审时，使用 delegate_task 并行启动三个隔离上下文的真实子 Agent：证据分析员、反方审查员、落地评审员。
        三者独立给出事实与假设、反例与盲点、成本与步骤。主 Agent 比较结论，只有高影响分歧才追加至多一轮定向复核，禁止开放式互聊。
        工具不可用时明确标注降级为单 Agent 审查，不伪造专家意见。保留子 Agent 的真实返回，最终答复不重复粘贴三份原文。
        最终输出：会审结论、共识、关键分歧与裁决、证据与风险、相比单 Agent 的增益、置信度与未决事项。
        [原始问题]
        $text
    """.trimIndent()
    else -> text
}
fun visibleUserText(text:String):String = presentUserText(text).body
