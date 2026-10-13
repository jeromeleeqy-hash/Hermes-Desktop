package com.qingyu.hermescompanion.desktop

import androidx.compose.runtime.*
import java.util.Locale
import org.json.JSONObject
import com.qingyu.hermescompanion.platform.DesktopHost
import com.qingyu.hermescompanion.i18n.resolveDesktopLanguage
import com.qingyu.hermescompanion.i18n.traditionalLabel
import com.qingyu.hermescompanion.i18n.localizedText

private var uiLanguage by mutableStateOf("zh")
private val englishLabels by lazy { JSONObject(object {}.javaClass.getResourceAsStream("/desktop-en.json")?.bufferedReader()?.use { it.readText() } ?: "{}") }
fun setDesktopLanguage(value:String) {
    uiLanguage=resolveDesktopLanguage(value)
    com.qingyu.hermescompanion.i18n.desktopLanguage=uiLanguage
}
private val shortcutLabels = setOf(
    "⌘ N 新对话 · ⌘ K 搜索 · ⌘ P 命令 · ⌘ S 保存 · ⌘ Enter 发送 · ⌘ , 设置",
    "把图片或文件拖入窗口，可以添加到当前对话。Enter 换行，⌘ Enter 发送。",
    "新对话 · ⌘ N", "设置 · ⌘ ,", "搜索 · ⌘ K", "快捷命令 · ⌘ P", "⌘ Enter 发送", "发送 · ⌘ Enter",
)
fun tr(text:String):String {
    val label = when(uiLanguage) {"en" -> englishLabels.optString(text,text);"zh-TW" -> traditionalLabel(text);else -> text}
    return if(text in shortcutLabels)DesktopHost.shortcutLabel(label)else label
}
/** Display-only localization: keep the stored title and every custom title unchanged. */
fun sessionDisplayTitle(title:String):String =
    if(com.qingyu.hermescompanion.ui.format.isPlaceholderSessionTitle(title)) {
        if(uiLanguage=="en")com.qingyu.hermescompanion.i18n.uiText(com.qingyu.hermescompanion.R.string.ui_0079,"新会话") else tr("新会话")
    }else title

fun desktopLocale():Locale=when(uiLanguage){"en"->Locale.ENGLISH;"zh-TW"->Locale.TAIWAN;else->Locale.CHINA}

internal fun councilPrompt(text:String,mode:String):String = when(mode) {
    "quick" -> localizedText({ """
        [Hermes Desktop · 快速会审]
        当前会话使用 MoA。比较参考模型真实、独立的返回结果，不虚构专家对话，不重复原始分析。
        最终输出：会审结论、共识、关键分歧与裁决、证据与风险、相比单模型的增益、置信度与未决事项。
        [原始问题]
        $text
    """.trimIndent() }, { """
        [Hermes Desktop · 快速會審]
        當前會話使用 MoA。比較參考模型真實、獨立的返回結果，不虛構專家對話，不重複原始分析。
        最終輸出：會審結論、共識、關鍵分歧與裁決、證據與風險、相比單模型的增益、置信度與未決事項。
        [原始問題]
        $text
    """.trimIndent() })
    "deep" -> localizedText({ """
        [Hermes Desktop · 深度专家会审]
        先判断是否值得会审；简单问题直接回答并说明无需会审。
        需要会审时，使用 delegate_task 并行启动三个隔离上下文的真实子 Agent：证据分析员、反方审查员、落地评审员。
        三者独立给出事实与假设、反例与盲点、成本与步骤。主 Agent 比较结论，只有高影响分歧才追加至多一轮定向复核，禁止开放式互聊。
        工具不可用时明确标注降级为单 Agent 审查，不伪造专家意见。保留子 Agent 的真实返回，最终答复不重复粘贴三份原文。
        最终输出：会审结论、共识、关键分歧与裁决、证据与风险、相比单 Agent 的增益、置信度与未决事项。
        [原始问题]
        $text
    """.trimIndent() }, { """
        [Hermes Desktop · 深度專家會審]
        先判斷是否值得會審；簡單問題直接回答並說明無需會審。
        需要會審時，使用 delegate_task 並行啟動三個隔離上下文的真實子 Agent：證據分析員、反方審查員、落地評審員。
        三者獨立給出事實與假設、反例與盲點、成本與步驟。主 Agent 比較結論，只有高影響分歧才追加至多一輪定向複核，禁止開放式互聊。
        工具不可用時明確標註降級為單 Agent 審查，不偽造專家意見。保留子 Agent 的真實返回，最終答覆不重複貼上三份原文。
        最終輸出：會審結論、共識、關鍵分歧與裁決、證據與風險、相比單 Agent 的增益、置信度與未決事項。
        [原始問題]
        $text
    """.trimIndent() })
    else -> text
}
fun visibleUserText(text:String):String = presentUserText(text).body

internal fun desktopLanguageLabel(value:String):String=when(value){"en"->"English";"zh"->"简体中文";"zh-TW"->"繁體中文";else->tr("跟随系统")}

internal fun displayBiography(value:String):String = if(value in setOf("个人工作助理","個人工作助理"))tr("个人工作助理")else value
