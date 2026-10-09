package com.qingyu.hermescompanion.data

import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.*
import okhttp3.mockwebserver.*
import org.json.JSONObject
import org.junit.*
import org.junit.Assert.*
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.TimeUnit

class ArtifactPaths203Test {
    private val relative="03_主题/老夫子翡翠深圳巡展/项目.md"
    private fun paths(text:String)=ChatInsightParser.artifactsFromText(text).map {it.path}

    @Test fun inlineRelativePathKeepsTheFirstDirectoryAndResolvesUnderTheWorkspace() {
        val artifact=ChatInsightParser.artifactsFromText("背景已写进 `$relative`").single()
        assertEquals(relative,artifact.path)
        assertEquals("项目.md",artifact.name)
        assertEquals("/root/workspace/$relative",resolveRemoteArtifactPath(artifact.path,"/root/workspace"))
    }
    @Test fun bareRelativePathsAreNeverReclassifiedAtAnInteriorSlash() {
        assertEquals(listOf(relative,"docs/reports/result.pdf"),paths("已保存 $relative 和 docs/reports/result.pdf。"))
        assertEquals(listOf("./$relative","../共享/项目.md"),paths("./$relative\n../共享/项目.md"))
    }
    @Test fun quotedPathsKeepChineseSpacesParenthesesAndLiteralFilenameCharacters() {
        val path="03_主题/深圳 展会(最终)/报价+方案#1%20.md"
        assertEquals(listOf(path),paths("已写入 `$path`"))
        assertEquals(listOf(path),paths("文件：\"$path\""))
        assertEquals("/work/$path",resolveRemoteArtifactPath(paths("`$path`").single(),"/work"))
    }
    @Test fun markdownLinksDecodeOnceWithoutChangingLiteralPlusOrEncodedPercentInTheFilename() {
        val text="[方案](03_%E4%B8%BB%E9%A2%98/%E6%8A%A5%E4%BB%B7+%2520%23%E7%89%88.md#section)"
        val path=paths(text).single()
        assertEquals("03_主题/报价+%20#版.md",path)
        assertEquals("/work/03_主题/报价+%20#版.md",resolveRemoteArtifactPath(path,"/work"))
    }
    @Test fun publicLinksAndLinkLabelsDoNotProduceServerFileCards() {
        assertTrue(paths("[docs/report.md](https://example.test/docs/report.md) https://example.test/files/a.pdf `https://example.test/abc.md`").isEmpty())
        assertTrue(paths("[email](mailto:report.pdf) `report.md.bak`").isEmpty())
    }
    @Test fun supportedAbsoluteAndUriSpellingsRemainIntact() {
        assertEquals(listOf("/root/workspace/项目.md","~/共享/方案.pdf"),paths("`/root/workspace/项目.md` 和 `~/共享/方案.pdf`"))
        assertEquals(listOf("/work/a b.pdf","/work/c.md","/work/d.pdf"),paths("file:///work/a%20b.pdf sandbox:/work/c.md MEDIA:/work/d.pdf"))
    }
    @Test fun windowsPathsRetainDriveAndUncRootAndHaveCleanDisplayNames() {
        val windows=listOf("C:\\工作\\项目.md","\\\\server\\share\\报告.pdf","资料\\项目.md")
        assertEquals(windows,paths(windows.joinToString("\n"){"`$it`"}))
        assertEquals(listOf("项目.md","报告.pdf","项目.md"),ChatInsightParser.artifactsFromText(windows.joinToString("\n"){"`$it`"}).map {it.name})
        assertEquals(windows.take(2),paths(windows.take(2).joinToString("\n")))
        assertEquals("C:\\work\\资料\\项目.md",resolveRemoteArtifactPath(windows.last(),"C:\\work"))
    }
    @Test fun identicalMarkdownAndCodeTargetsHaveOnlyOneCard() {
        assertEquals(listOf(relative),paths("`$relative` [项目.md]($relative)"))
    }

    private fun withReader(block:(HermesApiClient,MockWebServer,HermesSession)->Unit) {
        val dir=Files.createTempDirectory("hermes-artifact-paths-203")
        val server=MockWebServer().apply {start()}
        val client=HermesApiClient(ConnectionConfig(server.url("/prefix").toString(),"test"),SecureCookieJar(SecureConfigStore(dir){ByteArray(32){23}}))
        client.setProfile("other")
        try {block(client,server,HermesSession("session","原对话",profile="work",workspacePath="/root/workspace"))}
        finally {client.close();server.shutdown();dir.toFile().deleteRecursively()}
    }
    private fun response(body:String,status:Int=200)=MockResponse().setResponseCode(status).setHeader("Content-Type","application/json").setBody(body)
    private fun document(path:String)=response(JSONObject().put("path",path).put("name","项目.md").put("mime_type","text/markdown")
        .put("data_url","data:text/markdown;base64,"+Base64.getEncoder().encodeToString("# 正确文件".toByteArray())).toString())
    private fun item(s:HermesSession,path:String,message:String="m1")=RecentArtifact(s.profile,s.id,s.title,message,path,"项目.md","Markdown",s.workspacePath)
    private fun message(id:String="m1",path:String=relative)=ChatMessage(id=id,role=MessageRole.ASSISTANT,content="背景已写进 `$path`")
    private fun take(server:MockWebServer)=requireNotNull(server.takeRequest(2,TimeUnit.SECONDS))

    @Test fun readsTheCorrectServerPathAndOriginalProfileOverHttp()=withReader {client,server,s ->
        server.enqueue(document("/root/workspace/$relative"))
        val result=ArtifactFileReader(client).read(item(s,paths(message().content).single()),s,listOf(message()))
        val url=take(server).requestUrl!!
        assertEquals("/prefix/api/files/read",url.encodedPath)
        assertEquals("/root/workspace/$relative",url.queryParameter("path"))
        assertEquals("work",url.queryParameter("profile"))
        assertEquals("# 正确文件",result.content)
    }
    @Test fun oldCardRecoversFromItsOwnMessageEvenWhenAnotherMessageHasTheSameFilename()=withReader {client,server,s ->
        server.enqueue(response("{}",404));server.enqueue(document("/root/workspace/$relative"))
        val result=ArtifactFileReader(client).read(item(s,"/老夫子翡翠深圳巡展/项目.md"),s,listOf(message(),message("m2","其他项目/项目.md")))
        assertEquals("/root/workspace/$relative",result.path)
        assertEquals("/老夫子翡翠深圳巡展/项目.md",take(server).requestUrl!!.queryParameter("path"))
        assertEquals("/root/workspace/$relative",take(server).requestUrl!!.queryParameter("path"))
    }
    @Test fun uncachedOldCardLoadsTheOriginalMessageThenReconstructsTheCompletePath()=withReader {client,server,s ->
        server.enqueue(response("{}",404))
        server.enqueue(response("""{"session":{"message_count":1}}"""))
        server.enqueue(response("""{"messages":[{"id":"m1","role":"assistant","content":"背景已写进 `$relative`"}]}"""))
        server.enqueue(document("/root/workspace/$relative"))
        assertEquals("/root/workspace/$relative",ArtifactFileReader(client).read(item(s,"/老夫子翡翠深圳巡展/项目.md"),s,emptyList()).path)
        repeat(4){assertEquals("work",take(server).requestUrl!!.queryParameter("profile"))}
    }
    @Test fun ambiguousSameNameFilesAreNotGuessed()=withReader {client,server,s ->
        server.enqueue(response("{}",404))
        val value=item(s,"/项目.md",message="").copy(sessionId="")
        assertEquals(404,assertThrows(ApiException::class.java){ArtifactFileReader(client).read(value,s,listOf(message(),message("m2","其他项目/项目.md")))}.statusCode)
        assertEquals(1,server.requestCount)
    }
    @Test fun permissionFailureDoesNotTriggerAlternatePathRequests()=withReader {client,server,s ->
        server.enqueue(response("{}",403))
        assertEquals(403,assertThrows(ApiException::class.java){ArtifactFileReader(client).read(item(s,relative),s,listOf(message()))}.statusCode)
        assertEquals(1,server.requestCount)
    }
    @Test fun sourceFromAnotherProfileIsRejectedBeforeAnyFileRequest()=withReader {client,server,s ->
        assertThrows(IllegalArgumentException::class.java){ArtifactFileReader(client).read(item(s,relative),s.copy(profile="other"),listOf(message()))}
        assertEquals(0,server.requestCount)
    }
}
