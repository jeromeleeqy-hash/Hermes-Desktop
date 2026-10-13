package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.i18n.*
import com.qingyu.hermescompanion.model.DefaultPromptSnippets
import com.qingyu.hermescompanion.today.todayText
import org.json.JSONObject
import org.junit.After
import org.junit.Assert.*
import org.junit.Test
import java.util.Locale

class TraditionalChineseTest {
    private val prior = desktopLanguage
    @After fun restore() { setDesktopLanguage(prior) }

    @Test fun explicitAndSystemLanguagesKeepBothChineseScripts() {
        assertEquals("zh-TW", resolveDesktopLanguage("zh-TW", Locale.ENGLISH))
        for (tag in listOf("zh-TW", "zh-HK", "zh-MO", "zh-Hant", "zh-Hant-US"))
            assertEquals(tag, "zh-TW", resolveDesktopLanguage("system", Locale.forLanguageTag(tag)))
        for (tag in listOf("zh-CN", "zh-SG", "zh-Hans", "zh-Hans-TW"))
            assertEquals(tag, "zh", resolveDesktopLanguage("system", Locale.forLanguageTag(tag)))
        assertEquals("en", resolveDesktopLanguage("system", Locale.JAPAN))
        assertEquals("zh", resolveDesktopLanguage("zh", Locale.TAIWAN))
        setDesktopLanguage("zh-TW")
        assertEquals(Locale.TAIWAN, desktopLocale())
        assertEquals("繁體中文", desktopLanguageLabel("zh-TW"))
    }

    @Test fun labelsMessagesAndDefaultPromptsUseTraditionalChinese() {
        setDesktopLanguage("zh-TW")
        assertEquals("連線與使用指南", tr("连接与使用指南"))
        assertEquals("設定", tr("设置"))
        assertEquals("請填寫有效的提供商標識", uiText(19, "请填写有效的提供商标识"))
        assertEquals("附件 · 报告.md", uiText(76, "附件 · %1\$s", "报告.md"))
        assertEquals("同步首頁", todayText("同步首页", "Sync home"))
        assertTrue(DefaultPromptSnippets.first().text.contains("目標"))
    }

    @Test fun unknownUserTextAndTemplateArgumentsAreNeverTransliterated() {
        setDesktopLanguage("zh-TW")
        val original="用户的对话 /workspace/项目/报告.md https://example.com/连接"
        assertEquals(original, tr(original))
        var evaluations=0
        val value=localizedText({ error("Unselected template evaluated") }, { "檔案：${++evaluations} $original" })
        assertEquals(1, evaluations)
        assertEquals("檔案：1 $original", value)
        assertTrue(councilPrompt(original,"quick").contains(original))
        assertTrue(councilPrompt(original,"quick").contains("快速會審"))
        setDesktopLanguage("zh")
        assertEquals("设置",tr("设置"))
        setDesktopLanguage("en")
        assertEquals("Settings",tr("设置"))
    }

    @Test fun traditionalMessageCatalogHasEveryIdAndPreservesFormatArguments() {
        fun resource(name:String)=JSONObject(javaClass.getResourceAsStream("/$name.json")!!.bufferedReader().use {it.readText()})
        val source=resource("messages-zh");val translated=resource("messages-zh-TW")
        assertEquals(source.keySet(),translated.keySet())
        val specifier=Regex("%[0-9$]*[a-zA-Z]")
        source.keySet().forEach {key->
            assertEquals(key,specifier.findAll(source.getString(key)).map {it.value}.toList(),specifier.findAll(translated.getString(key)).map {it.value}.toList())
            assertFalse(key, translated.getString(key).startsWith("\""))
        }
    }

    @Test fun translatedTodayExamplesKeepSchemaAndSourcePaths() {
        for(name in listOf("hermes-today-examples.json","hermes-today-interactions.json")) {
            fun load(prefix:String)=JSONObject(javaClass.getResourceAsStream("/today/$prefix$name")!!.bufferedReader().use {it.readText()})
            val original=load("");val translated=load("zh-TW/")
            assertEquals(original.keySet(),translated.keySet())
            val sourceCards=original.getJSONArray("cards");val cards=translated.getJSONArray("cards")
            assertEquals(sourceCards.length(),cards.length())
            for(index in 0 until cards.length()) {
                val before=sourceCards.getJSONObject(index);val after=cards.getJSONObject(index)
                assertEquals(before.getString("id"),after.getString("id"))
                assertEquals(before.optJSONArray("sources")?.toString(),after.optJSONArray("sources")?.toString())
            }
        }
    }


    @Test fun everyTraditionalThemePreviewIsPackaged() {
        for (skin in listOf("office", "paper", "glass")) {
            javaClass.getResourceAsStream("/themes/$skin-zh-TW.png").use { stream ->
                assertNotNull(skin, stream)
                val bytes=stream!!.readNBytes(8)
                assertArrayEquals(byteArrayOf(-119,80,78,71,13,10,26,10),bytes)
            }
        }
    }


    @Test fun settingsSearchAcceptsTraditionalLabelsAndKeywords() {
        setDesktopLanguage("zh-TW")
        assertTrue(settingsSearchMatches("语音","語音"))
        assertTrue(settingsSearchMatches("语音","麥克風"))
        assertTrue(settingsSearchMatches("外观与账户","語言"))
        assertFalse(settingsSearchMatches("模型","麥克風"))
        setDesktopLanguage("en")
        assertTrue(settingsSearchMatches("语音","Voice"))
    }

    @Test fun cachedPlaceholderTitlesFollowLanguageWithoutMutatingCustomTitles() {
        setDesktopLanguage("zh-TW")
        for(title in listOf("新会话","新对话","新會話","新對話","untitled","new conversation",""))
            assertEquals(title,"新對話",sessionDisplayTitle(title))
        for(title in listOf("用户自定义标题","设置","新会话的记录","/workspace/报告.md"))
            assertEquals(title,sessionDisplayTitle(title))
        assertEquals("單擊",tr("单击"));assertEquals("雙擊",tr("双击"))
        assertEquals("拖入檔案",tr("拖入文件"))
        setDesktopLanguage("zh")
        assertEquals("新会话",sessionDisplayTitle("新對話"))
    }

    @Test fun savedBuiltInSnippetsLocalizeWithoutChangingEditedFields() {
        setDesktopLanguage("zh")
        val saved=DefaultPromptSnippets
        setDesktopLanguage("zh-TW")
        val translated=saved.map {com.qingyu.hermescompanion.model.localizedPromptSnippet(it)}
        assertEquals("總結重點",translated[1].title)
        assertEquals("先梳理目標，再給出可執行步驟",translated[0].text)
        val edited=saved[0].copy(title="用户自定名称",text="原始简体内容")
        assertEquals(edited,com.qingyu.hermescompanion.model.localizedPromptSnippet(edited))
        assertEquals("用户自定名称",com.qingyu.hermescompanion.model.localizedPromptSnippet(saved[0].copy(title="用户自定名称")).title)
        assertEquals("梳理目标",saved[0].title)
        assertEquals("恢復預設",tr("恢复默认"))
        assertEquals("個人工作助理",displayBiography("个人工作助理"))
        assertEquals("用户简介",displayBiography("用户简介"))
        assertEquals("標準",tr(reasoningLabel("medium")))
        assertEquals("讀取檔案",tr(toolLabel("read_file")))
        assertTrue(tr(sendKeyLabel(true)).contains("傳送"))
    }

}
