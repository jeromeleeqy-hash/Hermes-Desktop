@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class,androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.*
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.Density
import com.qingyu.hermescompanion.data.HermesApiClient
import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import okhttp3.mockwebserver.*
import java.io.File
import java.nio.file.Files

/** Real Compose rendering. All account, message and provider data here are local fixtures. */
object Render170Previews {
    private fun nodes(scene:ImageComposeScene):List<SemanticsNode> {
        fun walk(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::walk)
        return scene.semanticsOwners.flatMap {walk(it.unmergedRootSemanticsNode)}
    }
    private suspend fun frames(scene:ImageComposeScene,n:Int=24){repeat(n){scene.render(System.nanoTime()).close();delay(25)}}
    private fun click(scene:ImageComposeScene,label:String) {
        val p=nodes(scene).first {it.config.getOrNull(SemanticsProperties.Text)?.any {t->t.text==label}==true}.boundsInRoot.center
        scene.sendPointerEvent(PointerEventType.Move,p);scene.sendPointerEvent(PointerEventType.Press,p,button=PointerButton.Primary);scene.sendPointerEvent(PointerEventType.Release,p,button=PointerButton.Primary)
    }
    private val article="""
        # 把想法，写成下一步

        好的工作节奏，从一份清楚的记录开始。留一点空间思考，把重要的结论写下来，再把它们变成可以完成的事情。

        ## 01 · 让信息有自己的层次

        正文应该安静、易读。**清楚的标题**帮助我们定位，适当的行距让长段落也能轻松阅读。用 `Markdown` 记录想法，可以把注意力留给内容。

        > 不必把每件事都写得很长。保留背景、决定与下一步，就足以让协作继续。

        ### 这一周，专注三件事

        - 梳理已经确定的方向，补齐需要验证的假设。
        - 将讨论整理成一页文档，和伙伴保持一致。
        - 给每项行动安排负责人和一个明确的时间。

        | 阶段 | 交付内容 | 状态 |
        | :--- | :--- | :--- |
        | 发现 | 整理使用反馈与真实需求 | 已完成 |
        | 打磨 | 优化细节和关键操作路径 | 进行中 |
        | 验证 | 回归测试与交付检查 | 待开始 |

        ## 02 · 让下一步更具体

        一段简短的代码、一个具体的例子，都比模糊的承诺更有帮助。阅读和编辑放在一起时，滚动位置会跟着当前段落走。

        ```kotlin
        val nextStep = Action(
            title = "完成这一轮体验改进",
            owner = "Hermes",
            status = InProgress
        )
        ```

        ---

        *记录是一种延续：今天写下的想法，明天可以接着完成。*
    """.trimIndent()

    @JvmStatic fun main(args:Array<String>)=runBlocking<Unit>(Dispatchers.Swing) {
        setDesktopLanguage("zh")
        val destination=File(args.firstOrNull()?:"build/previews-1.7").apply {mkdirs()}
        val temp=Files.createTempDirectory("hermes-17-preview")
        val server=MockWebServer()
        server.dispatcher=object:okhttp3.mockwebserver.Dispatcher() {
            override fun dispatch(r:RecordedRequest):MockResponse {
                val body=when(r.requestUrl!!.encodedPath) {
                    "/api/env"->"""{"DEEPSEEK_API_KEY":{"is_set":true,"provider":"deepseek","provider_label":"DeepSeek","is_password":true},"OPENAI_API_KEY":{"is_set":false,"provider":"openai","provider_label":"OpenAI","is_password":true},"ANTHROPIC_API_KEY":{"is_set":false,"provider":"anthropic","provider_label":"Anthropic","is_password":true}}"""
                    "/api/providers/custom-endpoints"->"""{"endpoints":[{"id":"studio","name":"Studio API","base_url":"https://api.example.test/v1","model":"studio-chat","models":["studio-chat","studio-reasoner"],"has_api_key":true,"is_current":true}]}"""
                    else->"{}"
                }
                return MockResponse().setHeader("Content-Type","application/json").setBody(body)
            }
        }
        server.start()
        try {for(name in listOf("Markdown-Reading","Markdown-Split","Provider-Settings","Chat-Attachments")) {
            val providers=name=="Provider-Settings"
            val store=SecureConfigStore(temp.resolve(name)){ByteArray(32){17}}
            val c=DesktopController(!providers,store,autoConnect=false)
            c.changeLanguage("zh");c.reduceMotion=true;c.notifications=false;c.nickname="Jerome";c.setFilesPanel(false);c.decisions.clear()
            c.skin="轻盈办公";c.appearance="浅色"
            if(providers) {
                val api=HermesApiClient(ConnectionConfig(server.url("/").toString(),"preview"),SecureCookieJar(store))
                @Suppress("UNCHECKED_CAST")
                val clients=DesktopController::class.java.getDeclaredField("clients").apply {isAccessible=true}.get(c) as MutableMap<String,HermesApiClient>
                clients["default"]=api;c.connected=true;c.page=Page.PROFILE;c.settingsSection="模型供应商"
                c.modelCatalogError=modelCatalogProblem("模型列表暂时不可用")
            }else if(name.startsWith("Markdown")) {
                c.page=Page.FILES
                val t=c.document!!;c.document=t.copy(document=t.document.copy(name="把想法写成下一步.md",content=article,bytes=article.toByteArray()))
            }else {
                c.page=Page.CHAT
                c.userAvatar=store.saveBlob(File("src/main/resources/avatar.png").readBytes())
                val s=c.currentSession!!
                c.messages[s.scopedId]=listOf(ChatMessage(role=MessageRole.USER,content="请把这份需求整理成一份清楚的实施计划。"),ChatMessage(role=MessageRole.ASSISTANT,content="## 我们从这里开始\n\n我会先梳理目标和边界，再把工作拆成可以验收的步骤。你可以把参考资料一起发给我。\n\n- 确认要解决的实际问题\n- 排好实施顺序与依赖\n- 为每一步写下验收标准"))
                c.drafts[s.scopedId]="这是补充的需求和参考资料，请一起考虑。"
                c.attachments[s.scopedId]=listOf(PendingAttachment(id="preview-pdf",name="产品需求.pdf",mimeType="application/pdf",uploadDataUrl="data:application/pdf;base64,JVBERg=="),PendingAttachment(id="preview-doc",name="会议纪要.md",mimeType="text/markdown",textContent="需求与下一步"))
            }
            val scene=ImageComposeScene(1920,1200,density=Density(1.5f),coroutineContext=Dispatchers.Swing){HermesTheme(c){DesktopBackdrop{DesktopWorkspace(c)}}}
            try {
                frames(scene,40)
                if(name=="Markdown-Split"){click(scene,"双栏");frames(scene)}
                if(providers) {
                    check(nodes(scene).any {it.config.getOrNull(SemanticsProperties.Text)?.any {t->t.text=="Studio API"}==true}) {"Provider list did not render independently of the unavailable model catalog"}
                    check(nodes(scene).none {it.config.getOrNull(SemanticsProperties.Text)?.any {t->t.text=="还没有自定义供应商"}==true})
                }
                if(name=="Chat-Attachments")check(nodes(scene).any {it.config.getOrNull(SemanticsProperties.TestTag)=="chat-user-avatar"})
                File(destination,"$name.png").writeBytes(scene.render(System.nanoTime()).use {it.encodeToData()!!.use {d->d.bytes}})
                if(name=="Markdown-Reading") {
                    val p=nodes(scene).first {it.config.getOrNull(SemanticsProperties.TestTag)=="document-content"}.boundsInRoot.center
                    scene.sendPointerEvent(PointerEventType.Move,p)
                    scene.sendPointerEvent(PointerEventType.Scroll,p,scrollDelta=Offset(0f,18f));frames(scene)
                    File(destination,"Markdown-Details.png").writeBytes(scene.render(System.nanoTime()).use {it.encodeToData()!!.use {d->d.bytes}})
                }
                println("Rendered and checked $name")
            }finally {scene.close();c.close()}
        }}finally {server.shutdown();temp.toFile().deleteRecursively()}
    }
}
