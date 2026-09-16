package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.SecureConfigStore
import org.junit.Test
import org.junit.Assert.*
import java.time.*
import java.nio.file.Files

class DesktopExperienceTest {
    @Test fun oneAssistantTurnKeepsReasoningFilesAndSearchIds() {
        val messages=listOf(
            ChatMessage("u",MessageRole.USER,"生成文档"),
            ChatMessage("r1",MessageRole.ASSISTANT,"",reasoning="先查资料"),
            ChatMessage("t",MessageRole.TOOL,"private tool payload"),
            ChatMessage("r2",MessageRole.ASSISTANT,"",reasoning="整理结构"),
            ChatMessage("a",MessageRole.ASSISTANT,"完成了\nMEDIA:/work/report.md"),
            ChatMessage("u2",MessageRole.USER,"下一件事"),
            ChatMessage("a2",MessageRole.ASSISTANT,"处理中",isStreaming=true),
        )
        val turns=displayTurns(messages)
        assertEquals(4,turns.size)
        assertEquals(setOf("r1","r2","a"),turns[1].sourceIds)
        assertEquals("先查资料\n\n整理结构",turns[1].message.reasoning)
        assertTrue(turns.last().message.isStreaming)
        assertFalse(turns.joinToString().contains("private tool payload"))
        assertEquals("完成了",visibleAssistantText(turns[1].message.content))
    }
    @Test fun separateUserTurnsAndSystemBoundariesAreNeverMerged() {
        val values=listOf(ChatMessage("a",MessageRole.ASSISTANT,"一"),ChatMessage("s",MessageRole.SYSTEM,"boundary"),ChatMessage("b",MessageRole.ASSISTANT,"二"),ChatMessage("u",MessageRole.USER,"三"),ChatMessage("c",MessageRole.ASSISTANT,"四"))
        assertEquals(listOf("一","二","三","四"),displayTurns(values).map {it.message.content})
    }
    @Test fun transportMarkerDoesNotEraseCodeExamplesOrUnrecognizedContent() {
        val code="示例\n```text\nMEDIA:/work/demo.md\n```\nMEDIA:custom-without-path"
        assertEquals(code,visibleAssistantText(code))
        assertEquals("MEDIA:/work/demo.md 是一个示例",visibleAssistantText("MEDIA:/work/demo.md 是一个示例"))
    }
    @Test fun latestMeaningfulReplyBecomesConversationSummary() {
        val messages=listOf(ChatMessage(role=MessageRole.USER,content="最初的问题"),ChatMessage(role=MessageRole.ASSISTANT,content="已经完成第二版，请确认。"),ChatMessage(role=MessageRole.ASSISTANT,content="",reasoning="额外过程"))
        assertEquals("已经完成第二版，请确认。",sessionSummary(messages,"你好，测试一下"))
    }
    @Test fun artifactOrderIsIndependentOfOpeningOldConversations() {
        fun a(path:String,at:Long,profile:String="work")=RecentArtifact(profile,"s","标题",path=path,name=path,kind="document",seenAtMillis=at)
        val newer=a("/p/new.md",200);val older=a("/p/old.md",100)
        assertEquals(listOf(newer,older),mergeRecentArtifacts(listOf(newer),listOf(older)))
        assertEquals(2,mergeRecentArtifacts(listOf(newer),listOf(newer.copy(profile="private"))).size)
        assertEquals(listOf(newer),mergeRecentArtifacts(listOf(newer),listOf(newer)))
    }
    @Test fun projectFilterHonorsDirectoryBoundaries() {
        val project=HermesProject("p","项目","/work/demo")
        val a=RecentArtifact("default","s","x",path="/work/demo/a.md",name="a",kind="document")
        assertTrue(artifactMatchesProject(a,project))
        assertFalse(artifactMatchesProject(a.copy(path="/work/demo2/a.md"),project))
    }
    @Test fun weekdaySchedulesAndServerTimezoneArePreviewedCorrectly() {
        val choice=ScheduleChoice("工作日",9,30)
        assertEquals("30 9 * * 1-5",choice.expression())
        assertEquals("工作日 09:30",scheduleLabel(choice.expression()))
        val next=nextScheduledTimes(choice,ZoneId.of("Asia/Shanghai"),Instant.parse("2026-09-11T02:00:00Z"))
        assertEquals(LocalDate.of(2026,9,14),next.first().toLocalDate())
        assertEquals(ZoneOffset.ofHours(8),next.first().offset)
        assertEquals(3,next.size)
    }
    @Test fun sundayAliasAndSeveralWeekdaysAreSupported() {
        assertEquals(setOf(0),simpleSchedule("0 8 * * 7")!!.weekdays)
        assertEquals(setOf(1,3,5),simpleSchedule("45 17 * * 1,3,5")!!.weekdays)
        assertNull(simpleSchedule("*/15 * * * *"))
    }
    @Test fun invalidCronNeverPassesValidation() {
        listOf("60 9 * * *","0 24 * * *","0 9 0 * *","0 9 * 13 *","0 9 * * 8","*/0 * * * *","0 9 * * 5-1","0 9 * *","a 9 * * *").forEach {assertNotNull(it,cronValidation(it))}
        listOf("0 9 * * *","*/15 8-18 * * 1-5","0,30 12 1,15 * *","0 9 * * 7").forEach {assertNull(it,cronValidation(it))}
    }
    @Test fun dstPreviewUsesTheRequestedZoneAndActualInstants() {
        val next=nextScheduledTimes(ScheduleChoice("每天",2,30),ZoneId.of("America/New_York"),Instant.parse("2026-03-08T05:00:00Z"))
        assertEquals(3,next.first().hour)
        assertTrue(next.zipWithNext().all {(a,b)->a.toInstant()<b.toInstant()})
    }
    @Test fun invalidIntegerDraftSurvivesReopeningAndCannotLookClean() {
        var stored=""
        val draft=SettingDraft(ApprovalSettings(),{stored=it},SettingCodec::approval,SettingCodec::approval)
        draft.rawInputs["timeout"]="abc";draft.fieldErrors["timeout"]="请输入整数";draft.persist()
        val restored=SettingDraft(ApprovalSettings(),{},SettingCodec::approval,SettingCodec::approval,stored)
        assertTrue(restored.dirty);assertEquals("abc",restored.rawInputs["timeout"])
        restored.reset();assertFalse(restored.dirty);assertTrue(restored.rawInputs.isEmpty())
    }
    @Test fun editingDuringSaveRetainsTheLaterDraft() {
        val draft=SettingDraft(ApprovalSettings(),{},SettingCodec::approval,SettingCodec::approval)
        draft.value=draft.value.copy(timeoutSeconds=120)
        val submitted=draft.value
        draft.value=draft.value.copy(timeoutSeconds=180)
        draft.commit(submitted,submitted)
        assertTrue(draft.dirty);assertEquals(180,draft.value.timeoutSeconds);assertEquals(120,draft.baseline.timeoutSeconds)
    }
    @Test fun cleanNumericFieldFollowsARefreshedServerValue() {
        val draft=SettingDraft(ApprovalSettings(),{},SettingCodec::approval,SettingCodec::approval)
        draft.rawInputs["timeout"]="60"
        draft.rebase(ApprovalSettings(timeoutSeconds=90))
        assertTrue(draft.rawInputs.isEmpty());assertEquals(90,draft.value.timeoutSeconds)
    }
    @Test fun serverRefreshDoesNotOverwriteUnsavedSettings() {
        val draft=SettingDraft(ConversationStyleSettings(),{},SettingCodec::conversation,SettingCodec::conversation)
        draft.value=draft.value.copy(personality="我的修改")
        val server=ConversationStyleSettings(personality="手机端的修改")
        draft.rebase(server)
        assertTrue(draft.conflict);assertEquals("我的修改",draft.value.personality)
        draft.reset();assertEquals(server,draft.value);assertFalse(draft.dirty)
    }
    @Test fun matchingSubmittedSettingsBecomeCleanAfterSave() {
        var persisted=""
        val draft=SettingDraft(ApprovalSettings(),{persisted=it},SettingCodec::approval,SettingCodec::approval)
        draft.value=draft.value.copy(mode="always");draft.persist();assertTrue(persisted.isNotBlank())
        draft.commit(draft.value,draft.value)
        assertFalse(draft.dirty);assertEquals("",persisted)
    }
    @Test fun structuredSettingCodecRoundTripsAllModelFields() {
        val model=ServerModelSettings("vendor","model","high",12345,mapOf("vision" to ModelChoice("v","m")),listOf(FallbackModel("b","fallback")),listOf("first","second"),"summary")
        assertEquals(model,SettingCodec.model(SettingCodec.model(model)))
        val memory=MemoryContextSettings(false,true,5000,2400,true,.7,.25,6)
        assertEquals(memory,SettingCodec.memory(SettingCodec.memory(memory)))
    }
    @Test fun diffCanReconstructBothVersionsWithoutLosingBlankLines() {
        val before="# 文档\n\n原来的内容\n末尾\n";val after="# 文档\n\n更新的内容\n新增\n末尾\n"
        val diff=documentDiff(before,after)
        assertEquals(before,diff.filter {it.kind!='+'}.joinToString("\n"){it.text})
        assertEquals(after,diff.filter {it.kind!='-'}.joinToString("\n"){it.text})
        assertTrue(documentDiff(before,before).isEmpty())
    }
    @Test fun largeDiffHasBoundedMemoryAndPreservesContent() {
        val before=(1..1500).joinToString("\n"){"a$it"};val after=(1..1500).joinToString("\n"){"b$it"}
        val diff=documentDiff(before,after)
        assertEquals(before,diff.filter {it.kind!='+'}.joinToString("\n"){it.text})
        assertEquals(after,diff.filter {it.kind!='-'}.joinToString("\n"){it.text})
    }
    @Test fun fileSizesDistinguishUnknownTinyAndEmptyFiles() {
        assertEquals("大小未知",fileSizeLabel(null));assertEquals("0 B",fileSizeLabel(0));assertEquals("512 B",fileSizeLabel(512));assertEquals("1.5 KB",fileSizeLabel(1536))
    }
    @Test fun artifactIndexAndSummariesSurviveEncryptedCheckpoint() {
        val dir=Files.createTempDirectory("hermes-index")
        try {
            val store=SecureConfigStore(dir){ByteArray(32){17}}
            val state=WorkspaceState(revision=1,indexVersions=mapOf("work::s" to "date:42"),summaries=mapOf("work::s" to "私人工作进展"))
            WorkspaceRepository(store,"work").save(state)
            assertEquals(state,WorkspaceRepository(store,"work").load())
            Files.walk(dir).use {paths->paths.filter(Files::isRegularFile).forEach {assertFalse(String(Files.readAllBytes(it)).contains("私人工作进展"))}}
        }finally {Files.walk(dir).use {it.sorted(Comparator.reverseOrder()).forEach(Files::deleteIfExists)}}
    }
}
