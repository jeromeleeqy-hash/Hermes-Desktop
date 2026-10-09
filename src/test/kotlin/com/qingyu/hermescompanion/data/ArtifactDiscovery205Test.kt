package com.qingyu.hermescompanion.data

import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.*
import okhttp3.mockwebserver.*
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.Assert.*
import java.nio.file.Files
import java.util.Base64
import java.util.concurrent.TimeUnit

class ArtifactDiscovery205Test {
    private val root="/root/workspace"
    private val name="团队工作简报_20261009.md"
    private val actual="$root/05_原始素材/日报/团队/$name"
    private fun json(body:String,status:Int=200)=MockResponse().setResponseCode(status).setHeader("Content-Type","application/json").setBody(body)
    private fun doc(path:String)=json(JSONObject().put("path",path).put("name",artifactFileName(path)).put("mime_type","text/markdown")
        .put("data_url","data:text/markdown;base64,"+Base64.getEncoder().encodeToString("# 日报".toByteArray())).toString())
    private fun entry(path:String,dir:Boolean)=WorkspaceEntry(artifactFileName(path),path,dir)
    private fun listing(path:String,vararg children:WorkspaceEntry)=json(JSONObject().put("path",path).put("entries",JSONArray().apply {
        children.forEach {put(JSONObject().put("name",it.name).put("path",it.path).put("is_directory",it.isDirectory))}
    }).toString())
    private fun withReader(block:(HermesApiClient,MockWebServer,HermesSession,RecentArtifact,List<ChatMessage>)->Unit) {
        val temp=Files.createTempDirectory("hermes-artifact-discovery-205")
        val server=MockWebServer().apply {start()}
        val client=HermesApiClient(ConnectionConfig(server.url("/gateway").toString(),"test"),SecureCookieJar(SecureConfigStore(temp){ByteArray(32){23}}))
        client.setProfile("other")
        val session=HermesSession("source","原对话",profile="work",workspacePath=root)
        val item=RecentArtifact(session.profile,session.id,session.title,"m1",name,name,"Markdown",root)
        try {block(client,server,session,item,listOf(ChatMessage("m1",MessageRole.ASSISTANT,"已落盘 `$name`，时间线追上。")))}
        finally {client.close();server.shutdown();temp.toFile().deleteRecursively()}
    }
    private fun requests(server:MockWebServer)=List(server.requestCount){requireNotNull(server.takeRequest(2,TimeUnit.SECONDS))}

    @Test fun filenameOnlyReplyOpensTheUniqueFileThreeDirectoriesBelowTheSourceWorkspace()=withReader {client,server,s,item,messages->
        server.enqueue(json("{}",404))
        server.enqueue(listing(root,entry("$root/05_原始素材",true)))
        server.enqueue(listing("$root/05_原始素材",entry("$root/05_原始素材/日报",true)))
        server.enqueue(listing("$root/05_原始素材/日报",entry("$root/05_原始素材/日报/团队",true)))
        server.enqueue(listing("$root/05_原始素材/日报/团队",entry(actual,false)))
        server.enqueue(doc(actual))
        val result=ArtifactFileReader(client).read(item,s,messages)
        assertEquals(actual,result.path);assertEquals("# 日报",result.content)
        val sent=requests(server)
        assertEquals(listOf("$root/$name",root,"$root/05_原始素材","$root/05_原始素材/日报","$root/05_原始素材/日报/团队",actual),sent.map {it.requestUrl!!.queryParameter("path")})
        assertTrue(sent.all {it.method=="GET" && it.requestUrl!!.queryParameter("profile")=="work"})
    }
    @Test fun aFullPathInTheSameReplyTakesPrecedenceOverItsBareFilenameLabel()=withReader {client,server,s,item,_->
        server.enqueue(doc(actual))
        val message=ChatMessage("m1",MessageRole.ASSISTANT,"已保存 `$name`，位置：`$actual`")
        assertEquals(actual,ArtifactFileReader(client).read(item,s,listOf(message)).path)
        assertEquals(actual,requests(server).single().requestUrl!!.queryParameter("path"))
    }
    @Test fun confirmedLocationIsReadBeforeTheOriginalFilename()=withReader {client,server,s,item,messages->
        server.enqueue(doc(actual))
        assertEquals(actual,ArtifactFileReader(client).read(item.copy(path=actual,sourcePath=name),s,messages).path)
        assertEquals(actual,requests(server).single().requestUrl!!.queryParameter("path"))
    }
    @Test fun aMissingConfirmedLocationCanBeFoundAgainUnderTheOriginalScope()=withReader {client,server,s,item,messages->
        server.enqueue(json("{}",404));server.enqueue(json("{}",404))
        val moved="$root/archive/$name"
        server.enqueue(listing(root,entry("$root/archive",true)))
        server.enqueue(listing("$root/archive",entry(moved,false)));server.enqueue(doc(moved))
        assertEquals(moved,ArtifactFileReader(client).read(item.copy(path=actual,sourcePath=name),s,messages).path)
    }
    @Test fun duplicateNamesReturnChoicesInsteadOfReadingAnArbitraryFile()=withReader {client,server,s,item,messages->
        server.enqueue(json("{}",404))
        server.enqueue(listing(root,entry("$root/a",true),entry("$root/b",true)))
        server.enqueue(listing("$root/a",entry("$root/a/$name",false)))
        server.enqueue(listing("$root/b",entry("$root/b/$name",false)))
        val error=assertThrows(ArtifactLookupException::class.java){ArtifactFileReader(client).read(item,s,messages)}
        assertEquals(listOf("$root/a/$name","$root/b/$name"),error.candidates.map {it.path})
        assertTrue(error.message.contains("多个"));assertEquals(4,server.requestCount)
    }
    @Test fun oneMatchAndAnUnreadableDirectoryDoNotCountAsAUniqueMatch()=withReader {client,server,s,item,messages->
        server.enqueue(json("{}",404))
        server.enqueue(listing(root,entry("$root/a",true),entry("$root/private",true)))
        server.enqueue(listing("$root/a",entry("$root/a/$name",false)))
        server.enqueue(json("{}",403))
        val error=assertThrows(ArtifactLookupException::class.java){ArtifactFileReader(client).read(item,s,messages)}
        assertEquals(1,error.candidates.size);assertTrue(error.message.contains("无权"))
        assertTrue(error.details.contains("查找完成：false"));assertEquals(4,server.requestCount)
    }
    @Test fun invalidDirectoryResponsesDoNotBecomeAFalseMissingFileResult()=withReader {client,server,s,item,messages->
        server.enqueue(json("{}",404));server.enqueue(json("{}"))
        val error=assertThrows(ArtifactLookupException::class.java){ArtifactFileReader(client).read(item,s,messages)}
        assertTrue(error.details.contains("查找完成：false"));assertFalse(error.message.contains("删除"))
    }
    @Test fun aFullySearchedWorkspaceExplainsTheScopeWithoutClaimingDeletion()=withReader {client,server,s,item,messages->
        server.enqueue(json("{}",404));server.enqueue(listing(root))
        val error=assertThrows(ArtifactLookupException::class.java){ArtifactFileReader(client).read(item,s,messages)}
        assertTrue(error.message.contains("未找到"));assertTrue(error.details.contains(root))
        assertTrue(error.details.contains("查找完成：true"));assertTrue(error.candidates.isEmpty())
    }
    @Test fun directoryPathsOutsideTheSourceWorkspaceAreNeverRequested()=withReader {client,server,s,item,messages->
        server.enqueue(json("{}",404));server.enqueue(listing(root,entry("/other/project",true)))
        val error=assertThrows(ArtifactLookupException::class.java){ArtifactFileReader(client).read(item,s,messages)}
        assertTrue(error.message.contains("其他位置"));assertEquals(2,server.requestCount)
    }
    @Test fun explicitPathsAndEncodedLiteralFilenameCharactersDoNotTriggerDiscovery()=withReader {client,server,s,item,messages->
        val path="$root/日报/测试 + #1%20.md"
        server.enqueue(doc(path))
        assertEquals(path,ArtifactFileReader(client).read(item.copy(path=path),s,messages).path)
        assertEquals(path,requests(server).single().requestUrl!!.queryParameter("path"))
    }
    @Test fun limitsPreservePartialMatchesButNeverLabelThemComplete() {
        val search=ArtifactDiscovery({path,_->when(path){
            root->WorkspaceListing(path=root,entries=listOf(entry("$root/a",true),entry("$root/b",true)))
            else->WorkspaceListing(path=path,entries=listOf(entry("$path/$name",false)))
        }},maxDirectories=2).find(root,name)
        assertEquals(1,search.candidates.size);assertFalse(search.complete)
    }
    @Test fun timeBudgetStopsBeforeIssuingAnotherRequest() {
        var now=0L;var calls=0
        val search=ArtifactDiscovery({path,timeout->
            calls++;assertTrue(timeout<=3_000);now+=11_000
            WorkspaceListing(path=path,entries=listOf(entry("$root/a",true)))
        },clock={now}).find(root,name)
        assertFalse(search.complete);assertEquals(1,calls)
    }
    @Test fun filesystemRootsAndRelativeRootsAreNotRecursivelySearched() {
        val finder=ArtifactDiscovery({_,_->error("No requests expected")})
        for (path in listOf("/","C:\\","","relative"))assertFalse(finder.find(path,name).complete)
    }
    @Test fun windowsServerPathsStayInTheirOriginalWorkspace() {
        val windows="C:\\work";val nested="$windows\\日报";val calls=mutableListOf<String>()
        val search=ArtifactDiscovery({path,_->calls+=path;WorkspaceListing(path=path,entries=if(path==windows)listOf(entry(nested,true))else listOf(entry("$nested\\$name",false))) }).find(windows,name)
        assertTrue(search.complete);assertEquals(listOf(windows,nested),calls);assertEquals("$nested\\$name",search.candidates.single().path)
    }
}
