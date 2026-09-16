package com.qingyu.hermescompanion.desktop

import androidx.compose.runtime.*
import com.qingyu.hermescompanion.data.*
import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.storage.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.swing.Swing
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.collectLatest
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.security.MessageDigest
import java.util.UUID

enum class Page { HOME, SESSIONS, TASKS, FILES, PROFILE, CHAT }
data class DocumentTab(val document: WorkspaceDocument, val profile: String, val sourceSession: HermesSession? = null)
data class PendingDecision(val profile: String, val session: HermesSession, val request: AgentRequest)
data class DesktopRun(
    val session:HermesSession, val controller:StreamController, val assistantId:String,
    val started:Long=System.currentTimeMillis(), val status:String="正在连接", val lastEvent:Long=started,
    val record:RunRecord=RunRecord(session,"",assistantId=assistantId,started=started),
    val recovering:Boolean=false, val stopping:Boolean=false, val failed:Boolean=false,
    val tools:List<ToolActivity> = emptyList(), val candidate:String="", val candidateAt:Long=0,
)

@OptIn(FlowPreview::class)
class DesktopController(val demo:Boolean=false, val store:SecureConfigStore=SecureConfigStore(), private val autoConnect:Boolean=true, voiceAudio:DesktopAudio=DesktopAudio()) {
    internal val scope=CoroutineScope(SupervisorJob()+Dispatchers.Swing+CoroutineExceptionHandler { _,e -> error=e.message ?: "操作失败，内容已保留。" })
    internal var epoch=0
    private var config: ConnectionConfig? = null
    private var jar: SecureCookieJar? = null
    private val clients = mutableMapOf<String, HermesApiClient>()
    private val offsets = mutableMapOf<String, Int>()
    val queued = mutableStateMapOf<String,List<QueuedMessage>>()
    val failedSends = mutableStateMapOf<String,DraftRecord>()
    private var repository:WorkspaceRepository?=null
    private var revision=0L
    private var workspaceReady by mutableStateOf(false)
    private var closed=false
    private val dailySessionLock=Any()
    private val polling=mutableSetOf<String>()
    private val agentSyncing=mutableSetOf<String>()
    val unread=mutableStateListOf<String>()
    val openDocuments=mutableStateMapOf<String,DocumentTab>()
    val recentArtifacts=mutableStateListOf<RecentArtifact>()
    val sessionSummaries=mutableStateMapOf<String,String>()
    val indexedSessionVersions=mutableStateMapOf<String,String>()
    var artifactsIndexing by mutableStateOf(false)
    var artifactsIndexProgress by mutableStateOf("")
    var artifactsIndexError by mutableStateOf<String?>(null)
    private var artifactIndexJob:Job?=null
    var sessionsLoading by mutableStateOf(false)
    var sessionsLoadError by mutableStateOf<String?>(null)
    private var refreshToken = 0
    var sessionsSyncedAt by mutableStateOf<Long?>(null)
    var creatingSession by mutableStateOf(false)
        private set
    val attachmentLoads = mutableStateMapOf<String, Int>()
    val attachmentErrors = mutableStateMapOf<String, String>()
    private var filePickerOpen=false
    var sidebarCollapsed by mutableStateOf(false)
    var sidebarForcedOpen by mutableStateOf(false)
    internal var sidebarIsExpanded=false
    var focusMode by mutableStateOf(false)
    var settingsSection by mutableStateOf("外观与账户")
    var settingsSearch by mutableStateOf("")
    val settingsDrafts=mutableMapOf<String,SettingDraft<*>>()
    var sessionDrawer by mutableStateOf(false)
    var panelWidth by mutableStateOf(328f)
    var compact by mutableStateOf(true)
    var textScale by mutableStateOf(1f)
    var sendOnEnter by mutableStateOf(false)
    var composerHasComposition=false
    var activityMode by mutableStateOf("compact")
    var runningSendMode by mutableStateOf("queue")
    var showOutline by mutableStateOf(false)
    var sidebarQuery by mutableStateOf("")
    var sessionFilter by mutableStateOf("全部")
    var sessionPeriod by mutableStateOf(0)
    val readingPositions=mutableMapOf<String,ReadingPosition>()
    val modelSwitching=mutableStateMapOf<String,Boolean>()
    private val modelGenerations=mutableMapOf<String,Long>()
    val modelSwitchErrors=mutableStateMapOf<String,String>()
    val steering=mutableStateMapOf<String,Boolean>()
    val sendErrors=mutableStateMapOf<String,String>()
    val completedActivities=mutableStateMapOf<String,List<ToolActivity>>()
    var settingsLoading by mutableStateOf(false)
    var settingsError by mutableStateOf<String?>(null)
    var diagnosticBusy by mutableStateOf(false)
    val diagnosticDetails=mutableStateMapOf<String,String>()
    var documentSplit by mutableStateOf(false)
    var documentLoading by mutableStateOf(false)
    var detailsText by mutableStateOf<String?>(null)
    var detailsTitle by mutableStateOf("详情")
    var fileSection by mutableStateOf("目录")
    var showHiddenFiles by mutableStateOf(false)
    var fileSort by mutableStateOf("名称")
    val voiceNotes=mutableStateListOf<VoiceNote>()
    var focusMessageId by mutableStateOf<String?>(null)
    var appFocused by mutableStateOf(true)
    var commandsOpen by mutableStateOf(false)
    var assistantPanel by mutableStateOf(false)
    var filesPanelOpen by mutableStateOf(true)
        private set
    var filesLoading by mutableStateOf(false)
    var filesLoadError by mutableStateOf<String?>(null)
    var requestedFilesPath:String?=null
        private set
    var snippetsOpen by mutableStateOf(false)
    var councilMode by mutableStateOf("off")
    var reduceMotion by mutableStateOf(false)
    var floatingAssistantEnabled by mutableStateOf(true)
    val companion = DesktopCompanion(this)
    var userAvatar by mutableStateOf("")
    var hermesAvatar by mutableStateOf("")
    var hermesName by mutableStateOf("Hermes")
    var biography by mutableStateOf("个人工作助理")
    var language by mutableStateOf("system")
    var notificationMessages by mutableStateOf(true)
    var notificationTasks by mutableStateOf(true)
    var notificationSound by mutableStateOf(true)
    var notificationBadge by mutableStateOf(true)
    var snippets by mutableStateOf(DefaultPromptSnippets)
    var voicePreferences by mutableStateOf(VoicePreferences())
    var searchBusy by mutableStateOf(false)
    var sessionQuery by mutableStateOf("")
    var searchError by mutableStateOf<String?>(null)
    val messageLoading=mutableStateMapOf<String,Boolean>()
    val messageErrors=mutableStateMapOf<String,String>()
    private val messageReadTokens=mutableMapOf<String,Int>()
    var updateProgress by mutableStateOf<AgentUpdateProgress?>(null)
    var updating by mutableStateOf(false)
    var diagnosticLines by mutableStateOf(emptyList<Pair<String,Boolean>>())
    private val selectedProjects=mutableMapOf<String,String>()
    var page by mutableStateOf(Page.HOME)
    var connected by mutableStateOf(false)
    var connectionHealth by mutableStateOf("online")
    var connectionChecking by mutableStateOf(false)
    var reauthenticationOpen by mutableStateOf(false)
    private var connectionFailures=0
    private var nextConnectionProbe=0L
    var busy by mutableStateOf(false)
    var error by mutableStateOf<String?>(null)
    var loginFailure by mutableStateOf<GatewayLoginException?>(null)
        private set
    var notice by mutableStateOf<String?>(null)
    var baseUrl by mutableStateOf("")
    var username by mutableStateOf("")
    var profile by mutableStateOf("default")
    var profiles by mutableStateOf(emptyList<HermesProfile>())
    var sessions by mutableStateOf(emptyList<HermesSession>())
    var archived by mutableStateOf(emptyList<HermesSession>())
    var archivedLoading by mutableStateOf(false)
    var archivedError by mutableStateOf<String?>(null)
    private var archiveToken=0
    var showArchived by mutableStateOf(false)
    var projects by mutableStateOf(emptyList<HermesProject>())
    var project by mutableStateOf<HermesProject?>(null)
    var currentSession by mutableStateOf<HermesSession?>(null)
    val messages = mutableStateMapOf<String, List<ChatMessage>>()
    val drafts = mutableStateMapOf<String, String>()
    val attachments = mutableStateMapOf<String, List<PendingAttachment>>()
    val runs = mutableStateMapOf<String, DesktopRun>()
    val decisions = mutableStateMapOf<String, PendingDecision>()
    var completions by mutableStateOf(emptyList<RunCompletionSummary>())
    var cronJobs by mutableStateOf(emptyList<CronJob>())
    var cronSessions by mutableStateOf(emptyList<HermesSession>())
    var taskTab by mutableStateOf("执行中心")
    var listing by mutableStateOf<WorkspaceListing?>(null)
    var document by mutableStateOf<DocumentTab?>(null)
    val edits = mutableStateMapOf<String, String>()
    var editing by mutableStateOf(false)
    var savingDocument by mutableStateOf(false)
    var flushEditor: ((() -> Unit) -> Unit)? = null
    private var documentReadToken = 0
    private var browseToken = 0
    var settings by mutableStateOf<ServerSettings?>(null)
    var modelCatalog by mutableStateOf(ModelCatalog())
    var modelCatalogLoading by mutableStateOf(false)
    var modelCatalogError by mutableStateOf<ModelCatalogProblem?>(null)
    var modelCatalogLoadedAt by mutableStateOf<Long?>(null)
    private var modelCatalogToken=0
    var skills by mutableStateOf(emptyList<ServerSkill>())
    var toolsets by mutableStateOf(emptyList<ToolsetInfo>())
    var mcpServers by mutableStateOf(emptyList<McpServerInfo>())
    var commands by mutableStateOf(emptyList<SlashCommand>())
    var gateway by mutableStateOf(GatewayInfo())
    var updateInfo by mutableStateOf<AgentUpdateInfo?>(null)
    var searchResults by mutableStateOf<List<SessionSearchResult>?>(null)
    val voice=DesktopVoice(this,voiceAudio)
    val recording get()=voice.phase==VoicePhase.LISTENING
    val voiceBusy get()=voice.phase==VoicePhase.TRANSCRIBING
    var continuousVoice:Boolean
        get()=voice.active
        set(value) { if(value) voice.open() else voice.dismiss() }
    var nickname by mutableStateOf("Jerome")
    var appearance by mutableStateOf("浅色")
    var skin by mutableStateOf("轻盈办公")
    var notifications by mutableStateOf(true)
    private var searchJob: Job? = null
    private var profileJob: Job? = null

    init {
        if (demo) seedDemo() else try {
            baseUrl = store.get("baseUrl"); username = store.get("username")
            nickname = store.get("nickname", "Jerome"); appearance = store.get("appearance", "浅色"); skin = store.get("workspaceSkinV2", "轻盈办公")
            notifications = store.get("notifications", "true") == "true"
            profile = store.get("profile", "default")
            loadLocalPreferences()
            filesPanelOpen=store.get("filesPanelOpen","true")=="true"
            if (autoConnect && baseUrl.isNotBlank()) connect(baseUrl, username, null)
        } catch (e: Exception) { error = "无法读取本机配置：${e.message}" }
        scope.launch { while(isActive) {
            delay(5_000)
            if(connected&&!demo) {
                if(System.currentTimeMillis()>=nextConnectionProbe)checkConnection()
                if(connectionHealth=="online")reconcileRuns()
            }
        } }
        if(!demo) scope.launch {
            snapshotFlow { if(workspaceReady) checkpoint(0) else null }.debounce(450).collectLatest { value ->
                if(value!=null) { val repo=repository;val next=value.copy(revision=++revision)
                    try { withContext(Dispatchers.IO) { repo?.save(next) } } catch(e:CancellationException){throw e} catch(e:Exception){ error="本机内容保存失败：${e.message}" }
                }
            }
        }
    }
    internal fun scopeKey():String=accountScope(baseUrl,username)
    internal fun storageKey(key: String) = "${scopeKey()}:$key"
    internal fun client(p: String = profile): HermesApiClient = clients.getOrPut(p) {
        HermesApiClient(requireNotNull(config), requireNotNull(jar), store).also { it.setProfile(p);bindAgentEvents(it) }
    }
    private fun bindAgentEvents(api:HermesApiClient) {
        val token=epoch
        api.onUnboundAgentEvent={session,event->scope.launch {
            if(token==epoch&&!closed)receiveAgentControl(session,event)
        }}
    }
    internal fun receiveAgentControl(session:HermesSession,event:StreamEvent) {
        val key=session.scopedId
        when(event) {
            is StreamEvent.AgentRequestPending->{
                val id="$key:${event.request.requestId}"
                val previous=decisions[id]
                decisions[id]=PendingDecision(session.profile,session,event.request.copy(isResponding=previous?.request?.isResponding==true))
                if(runs[key]==null) {
                    val stream=StreamController().also {it.runtimeSessionId=event.request.runtimeSessionId}
                    val record=RunRecord(session,"",runtimeId=event.request.runtimeSessionId)
                    runs[key]=DesktopRun(session,stream,record.assistantId,record=record,recovering=true,status="等待你的处理")
                }else runs[key]?.let {runs[key]=it.copy(status="等待你的处理",candidate="")}
                if(sendErrors[key]?.startsWith("服务器中的上一个任务仍在运行")==true)sendErrors.remove(key)
                if(previous==null&&notifications&&notificationTasks)DesktopNotifications.show("Hermes 需要你确认",event.request.title,notificationSound)
            }
            is StreamEvent.AgentRequestExpired->{
                decisions.entries.removeAll {it.value.session.scopedId==key&&it.value.request.requestId==event.requestId}
                if(decisions.values.none {it.session.scopedId==key})runs[key]?.let {runs[key]=it.copy(status="正在继续处理",lastEvent=System.currentTimeMillis())}
            }
            else->Unit
        }
    }
    private fun synchronizeDecisions(snapshot:ResumedSession) {
        if(!snapshot.requestSnapshotKnown)return
        val live=snapshot.pendingRequests.map {it.requestId}.toSet()
        decisions.entries.removeAll {it.value.session.scopedId==snapshot.session.scopedId&&it.value.request.requestId !in live}
    }
    private fun refreshAgentState(session:HermesSession) {
        val key=session.scopedId
        if(demo||!connected||!agentSyncing.add(key))return
        val generation=modelGenerations[key]?:0L
        request(session.profile,{it.resumeSession(session)},finished={agentSyncing.remove(key)},failed={ /* the next recovery tick can retry */ }) {result->
            if(generation==(modelGenerations[key]?:0L)&&modelSwitching[key]!=true) {
                sessions=sessions.map {if(it.scopedId==key)result.session else it}
                if(currentSession?.scopedId==key)currentSession=result.session
            }
            synchronizeDecisions(result)
            result.pendingRequests.forEach {receiveAgentControl(result.session,StreamEvent.AgentRequestPending(it))}
            if(result.running==true&&runs[key]==null) {
                val stream=StreamController().also {it.runtimeSessionId=result.session.runtimeId}
                val record=RunRecord(result.session,"",baseline=messages[key].orEmpty().lastOrNull {it.role==MessageRole.ASSISTANT}?.recoverySignature().orEmpty(),runtimeId=result.session.runtimeId)
                runs[key]=DesktopRun(result.session,stream,record.assistantId,record=record,recovering=true,status="正在恢复服务器任务")
            }
        }
    }
    fun <T> request(p: String = profile, block: (HermesApiClient) -> T, finished: () -> Unit = {}, failed: ((String)->Unit)? = null, done: (T) -> Unit = {}) {
        if (demo) { notice = "这是布局预览，请连接网关后操作。"; finished(); return }
        if (!connected) { error = "请先连接 Hermes 网关。"; finished(); return }
        val token = epoch
        val api = client(p)
        scope.launch {
            try { val result = withContext(Dispatchers.IO) { block(api) }; if (token == epoch) done(result) }
            catch (e: CancellationException) { throw e }
            catch (e: Exception) { if (token == epoch) {if(e is ApiException&&e.statusCode==401)connectionHealth="auth";val message=e.message ?: "请求失败，请重试。";if(failed!=null)failed(message)else error=message} }
            finally { if (token == epoch) finished() }
        }
    }
    fun checkConnection() {
        if(demo||!connected||connectionChecking||connectionHealth=="auth")return
        connectionChecking=true
        val token=epoch;val api=client();val runtime=currentSession?.runtimeId
        scope.launch {
            try {
                withContext(Dispatchers.IO){api.probeConnection(runtime)}
                if(token!=epoch)return@launch
                val recovering=connectionHealth!="online"
                connectionHealth="online";connectionFailures=0;nextConnectionProbe=System.currentTimeMillis()+15_000
                if(recovering){reconcileRuns(force=true);currentSession?.let {refreshAgentState(it)}}
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(token==epoch){
                connectionHealth=if(e is ApiException&&e.statusCode==401)"auth"else"offline"
                connectionFailures=(connectionFailures+1).coerceAtMost(4)
                nextConnectionProbe=System.currentTimeMillis()+minOf(60_000L,5_000L*(1L shl connectionFailures))
            }}finally{if(token==epoch)connectionChecking=false}
        }
    }
    fun reauthenticate(password:String) {
        if(connectionChecking)return
        connectionChecking=true;val token=epoch;val api=client()
        scope.launch {
            try {
                withContext(Dispatchers.IO){api.login(username,password)}
                if(token!=epoch)return@launch
                connectionHealth="online";connectionFailures=0;reauthenticationOpen=false
                // Keep the workspace and pending drafts intact. Never resubmit messages here.
                reconcileRuns(force=true);currentSession?.let {refreshAgentState(it)}
            }catch(e:CancellationException){throw e}
            catch(e:Exception){if(token==epoch)error=e.message?:"重新登录失败，内容已保留。"}
            finally{if(token==epoch)connectionChecking=false}
        }
    }
    fun refreshDecision(value:PendingDecision){refreshAgentState(value.session)}
    fun setReasoning(s:HermesSession,effort:String) {
        val key=s.scopedId
        if(modelSwitching[key]==true||runs.containsKey(key))return
        modelGenerations[key]=(modelGenerations[key]?:0L)+1
        modelSwitching[key]=true;modelSwitchErrors.remove(key)
        request(s.profile,{it.changeSessionReasoning(s,effort)},finished={modelSwitching.remove(key)},failed={modelSwitchErrors[key]=it}){updated->
            sessions=sessions.map {if(it.scopedId==key)updated else it}
            if(currentSession?.scopedId==key)currentSession=updated
            notice="已更新当前对话的思考强度"
        }
    }
    fun connect(url: String, account: String, password: String?) {
        if (busy) return
        loginFailure = null
        if (password != null) {
            try { validateGatewayCredentials(account, password) }
            catch (e: GatewayLoginException) { loginFailure=e; error=e.message; return }
        }
        val clean = url.trim().trimEnd('/')
        val parsed = runCatching { java.net.URI(clean) }.getOrNull()
        if (parsed?.scheme !in setOf("https", "http") || parsed?.host.isNullOrBlank() || parsed?.userInfo != null || parsed?.query != null || parsed?.fragment != null) {
            error = "请填写完整的 HTTP 或 HTTPS 网关地址，不要附带账号、查询参数或锚点。"; return
        }
        if(!flushCheckpoint())return; workspaceReady=false
        val token = ++epoch
        creatingSession=false;attachmentLoads.clear();attachmentErrors.clear();messageLoading.clear();messageErrors.clear()
        busy = true; connected = false; error = null
        connectionChecking=false;reauthenticationOpen=false;connectionHealth="online";connectionFailures=0;nextConnectionProbe=0
        clients.values.forEach { it.close() }; clients.clear()
        val candidateConfig = ConnectionConfig(clean, account.trim())
        config = candidateConfig
        scope.launch {
            var candidate: HermesApiClient? = null
            try {
                val result = withContext(Dispatchers.IO) {
                    val cookies = gatewayLoginStep(GatewayLoginStage.LOCAL_SESSION) {
                        if (store.get("baseUrl") != clean || store.get("username") != candidateConfig.username) store.clearCookies()
                        SecureCookieJar(store)
                    }
                    val api = HermesApiClient(candidateConfig, cookies, store).also { candidate = it; it.setProfile(profile);bindAgentEvents(it) }
                    if (password != null) api.login(candidateConfig.username, password) else api.checkSavedSession()
                    gatewayLoginStep(GatewayLoginStage.LOCAL_SESSION) {
                        store.put("baseUrl", clean); store.put("username", candidateConfig.username)
                    }
                    Triple(api, cookies, gatewayLoginStep(GatewayLoginStage.PROFILES) { api.listProfiles() })
                }
                if (token != epoch) { result.first.close(); return@launch }
                baseUrl = clean; username = account.trim(); jar = result.second
                profiles = result.third
                if (profiles.none { it.name == profile }) profile = profiles.firstOrNull()?.name ?: "default"
                result.first.setProfile(profile); clients[profile] = result.first
                connected = true; savingDocument=false; messages.clear(); drafts.clear(); attachments.clear(); runs.clear(); decisions.clear(); document = null;currentSession=null; edits.clear()
                unread.clear();queued.clear();voiceNotes.clear();openDocuments.clear();recentArtifacts.clear();completions=emptyList()
                settingsDrafts.clear();sessionSummaries.clear();indexedSessionVersions.clear();readingPositions.clear();modelSwitching.clear();modelGenerations.clear();modelSwitchErrors.clear();sendErrors.clear();completedActivities.clear();steering.clear()
                repository=WorkspaceRepository(store,scopeKey())
                val saved=withContext(Dispatchers.IO) { repository!!.load() }
                restoreWorkspace(saved)
                restoreDecisions(); workspaceReady=true; loadProfile(profile)
                saved.selectedSession?.takeIf { it.profile==profile }?.let { openSession(it) }
                reconcileRuns(force=true)
            } catch (e: CancellationException) { candidate?.close(); throw e }
            catch (e: Exception) { candidate?.close(); if (token == epoch) {connected=false;workspaceReady=false;loginFailure=e as? GatewayLoginException;error = e.message ?: "连接失败"} }
            finally { if (token == epoch) busy = false }
        }
    }
    fun disconnect() {
        companion.reset()
        voice.close(); if(!flushCheckpoint())return; workspaceReady=false
        artifactIndexJob?.cancel();artifactsIndexing=false;settingsDrafts.clear()
        epoch++; creatingSession=false;attachmentLoads.clear();attachmentErrors.clear();messageLoading.clear();messageErrors.clear(); clients.values.forEach { it.close() }; clients.clear(); config=null; connected=false
        runs.clear(); decisions.clear(); messages.clear(); attachments.clear(); document=null; edits.clear(); drafts.clear();queued.clear();openDocuments.clear()
        runCatching { store.clearCookies() }.onFailure { error=it.message }
    }
    fun loadProfile(p: String) {
        if(p!=profile)companion.reset()
        sessionsSyncedAt=null
        archiveToken++;archivedLoading=false;archivedError=null;showArchived=false
        searchJob?.cancel();searchBusy=false;searchError=null
        if(p!=profile)voice.dismiss()
        project?.let { selectedProjects[profile]=it.id;savePreference(storageKey("project:$profile"),it.id) }
        artifactIndexJob?.cancel();artifactsIndexing=false;artifactsIndexError=null
        modelCatalogToken++;modelCatalog=ModelCatalog();modelCatalogLoading=false;modelCatalogError=null;modelCatalogLoadedAt=null
        settingsError=null;sidebarQuery="";sessionFilter="全部";sessionPeriod=0
        profile = p; project = null; currentSession = null; document = null; listing = null; filesLoadError=null; searchResults = null; page = Page.HOME
        sessionDrawer=false;documentSplit=false;settingsSearch="";sessionQuery=""
        sessions = emptyList(); projects = emptyList(); settings = null; cronJobs = emptyList();cronSessions=emptyList(); archived = emptyList()
        savePreference("profile", p)
        refresh()
        loadModelCatalog(force=true)
        request(p, { it.gatewayInfo() }) { if (profile == p) gateway = it }
    }
    fun refresh() {
        val p = profile
        val token = ++refreshToken
        val connectionEpoch = epoch
        sessionsLoading=true;sessionsLoadError=null
        request(p, { api -> api.listAllSessions { loaded ->
            scope.launch {
                if(epoch==connectionEpoch && profile==p && token==refreshToken && sessionsLoading) {
                    // Merge while loading; only a completed sync can remove old entries.
                    val visible=(loaded+sessions+cronSessions).distinctBy { it.scopedId }
                    sessions=visible.filter { it.source!="cron" }
                    cronSessions=visible.filter { it.source=="cron" }
                }
            }
        } },finished={if(profile==p&&token==refreshToken)sessionsLoading=false},failed={if(profile==p&&token==refreshToken)sessionsLoadError=it}) {
            if (profile == p&&token==refreshToken) {sessions=it.filter {s->s.source!="cron"};cronSessions=it.filter {s->s.source=="cron"};sessionsSyncedAt=System.currentTimeMillis();if(sessionQuery.isNotBlank()&&!showArchived)search(sessionQuery);syncArtifactIndex()}
        }
        request(p, { it.projectCatalog() }) { if (profile == p&&token==refreshToken) { projects=it;val selected=selectedProjects[p] ?: if(demo)"" else store.get(storageKey("project:$p"));project=it.firstOrNull { candidate->candidate.id==selected } } }
        request(p, { it.listCronJobs(p) }) { if (profile == p&&token==refreshToken) cronJobs = it }
    }
    fun chooseProject(value:HermesProject?,navigate:Boolean=true) { project=value;selectedProjects[profile]=value?.id.orEmpty();savePreference(storageKey("project:$profile"),value?.id.orEmpty());if(navigate)page=Page.SESSIONS;listing=null;filesLoadError=null }
    fun loadArchived() {
        val p=profile;val token=++archiveToken;archivedLoading=true;archivedError=null
        val connectionEpoch=epoch
        request(p,{api -> api.listArchivedSessions { loaded ->
            scope.launch {
                if(epoch==connectionEpoch && profile==p && token==archiveToken && archivedLoading)
                    archived=(loaded+archived).distinctBy { it.scopedId }
            }
        }},finished={if(token==archiveToken)archivedLoading=false},failed={if(token==archiveToken&&profile==p)archivedError=it}) {
            if(token==archiveToken&&profile==p){archived=it;if(sessionQuery.isNotBlank())search(sessionQuery)}
        }
    }
    fun newSession(daily:Boolean=false, activate:Boolean=true, onOpened:(HermesSession)->Unit={}) {
        if(creatingSession)return
        if(demo) {
            val s=if(daily)sessions.firstOrNull {it.title=="日常助理"&&it.profile==profile}?:HermesSession(UUID.randomUUID().toString(),"日常助理",profile=profile)
                else HermesSession(UUID.randomUUID().toString(),"新对话",profile=profile,workspacePath=project?.primaryPath.orEmpty())
            sessions=(listOf(s)+sessions).distinctBy {it.scopedId};if(activate)openSession(s);onOpened(s);return
        }
        creatingSession=true
        val p = profile; val path = project?.primaryPath
        request(p, { api ->
            if (daily) {
                resolveDailySession(api,p)
            } else api.createSessionForProfile(path,p)
        },finished={creatingSession=false}) { if (p == profile) { sessions = (listOf(it)+sessions).distinctBy { s -> s.scopedId };if(activate)openSession(it)else loadSessionMessages(it);onOpened(it) } }
    }
    internal fun resolveDailySession(api:HermesApiClient,p:String):HermesSession = synchronized(dailySessionLock) {
        val key=storageKey("daily:$p")
        val known=store.get(key).takeIf {it.isNotBlank()}?.let {id->try {api.sessionForProfile(id,p)}catch(e:ApiException){if(e.statusCode==404)null else throw e}}
        val session=known?:api.findSessionByTitleForProfile("日常助理",p)?:api.createSessionForProfile(null,p).also {
            api.renameSessionForProfile(it.id,"日常助理",p)
        }.copy(title="日常助理")
        store.put(key,session.id);session
    }
    fun homeDraftKey() = "home:$profile:${project?.id.orEmpty()}"
    fun addHomePrompt(prompt:String) {
        val key=homeDraftKey();val current=drafts[key].orEmpty()
        setDraft(key,if(current.isBlank())prompt else current.trimEnd()+"\n\n"+prompt)
    }
    fun startFromHome() {
        val key=homeDraftKey();val text=drafts[key].orEmpty();val files=attachments[key].orEmpty()
        if(creatingSession || (attachmentLoads[key]?:0)>0 || (text.isBlank()&&files.isEmpty()))return
        newSession { s ->
            setDraft(s.scopedId,text);attachments[s.scopedId]=files
            if(drafts[key]==text)drafts.remove(key)
            attachments[key]=attachments[key].orEmpty().filterNot { a->files.any {it.id==a.id} }
            // Persist the transfer before any network submission. Failed sends retain the draft.
            if(flushCheckpoint())send()
        }
    }
    fun toggleSidebar() {
        sidebarCollapsed=sidebarIsExpanded
        sidebarForcedOpen=!sidebarIsExpanded
        if(focusMode)focusMode=false
        savePreference("sidebarCollapsed",sidebarCollapsed.toString())
    }
    fun openSession(s:HermesSession, messageId:String?=null) {
        if(s.profile!=profile)loadProfile(s.profile)
        currentSession=s; navigate(Page.CHAT); focusMessageId=messageId;unread.remove(s.scopedId)
        sessionDrawer=false
        if(voice.sessionKey!=null && voice.sessionKey!=s.scopedId)voice.dismiss()
        loadSessionMessages(s,messageId)
    }
    internal fun loadSessionMessages(s:HermesSession,messageId:String?=null) {
        if(!runs.containsKey(s.scopedId))refreshAgentState(s)
        if (!drafts.containsKey(s.scopedId)) drafts[s.scopedId] = runCatching { store.get(storageKey("draft:${s.scopedId}")) }.getOrDefault("")
        if(demo)return
        if(runs.containsKey(s.scopedId) && messages[s.scopedId]?.isNotEmpty()==true)return
        val key=s.scopedId;val readToken=(messageReadTokens[key]?:0)+1;messageReadTokens[key]=readToken
        messageLoading[key]=true;messageErrors.remove(key)
        request(s.profile, { if(messageId==null)it.loadRecentMessagePage(s) else it.loadRecentMessagePage(s,200) },finished={if(messageReadTokens[key]==readToken)messageLoading[key]=false},failed={if(messageReadTokens[key]==readToken)messageErrors[key]=it}) { result ->
            if (messageReadTokens[key]==readToken&&runs[key]?.recovering!=false) {
                val cached=messages[key].orEmpty()
                val overlap=result.messages.firstOrNull()?.id?.let {id->cached.indexOfFirst {it.id==id}} ?: -1
                // Keep already loaded history when the refreshed tail still overlaps it.
                messages[key]=if(overlap>0)cached.take(overlap)+result.messages else result.messages
                offsets[key]=if(overlap>0)minOf(offsets[key]?:result.offset,result.offset)else result.offset
                indexArtifacts(s,messages[key].orEmpty())
            }
        }
        request(s.profile, { it.slashCommands() }) { if (currentSession?.scopedId == s.scopedId) commands = it }
    }
    fun hasOlder(s: HermesSession) = (offsets[s.scopedId] ?: 0) > 0
    fun loadOlder(s: HermesSession) {
        val key=s.scopedId
        if(messageLoading[key]==true||!hasOlder(s))return
        val readToken=messageReadTokens[key]
        val start = ((offsets[s.scopedId] ?: 0) - 60).coerceAtLeast(0)
        messageLoading[key]=true;messageErrors.remove(key)
        request(s.profile, { it.loadMessagePage(s,60,start) },finished={if(messageReadTokens[key]==readToken)messageLoading[key]=false},failed={if(messageReadTokens[key]==readToken)messageErrors[key]=it}) { page ->
            if(messageReadTokens[key]==readToken){messages[key]=(page.messages+messages[key].orEmpty()).distinctBy {it.id};offsets[key]=start;indexArtifacts(s,messages[key].orEmpty())}
        }
    }
    fun setDraft(key:String,value:String) { drafts[key]=value }
    fun removeAttachment(key:String,id:String) { attachments[key]=attachments[key].orEmpty().filterNot { it.id==id } }
    fun insertSnippet(text:String) { currentSession?.let { s -> setDraft(s.scopedId,(drafts[s.scopedId].orEmpty()+"\n"+text).trim()) } }
    fun addFiles(files: List<File>) {
        val key=if(page==Page.HOME)homeDraftKey()else currentSession?.scopedId
            ?: run {notice="先打开一段对话，再添加附件。";return}
        addFilesToDraft(key,files)
    }
    fun addFilesToDraft(key:String,files:List<File>) {
        if(files.isEmpty())return
        attachmentErrors.remove(key)
        attachmentLoads[key]=(attachmentLoads[key]?:0)+1
        val token=epoch
        scope.launch {
            try {
                require(attachments[key].orEmpty().size + files.size <= 10) { "每次最多添加 10 个附件。" }
                val results = withContext(Dispatchers.IO) { files.map {file->runCatching {DesktopFiles.attachment(file)}} }
                val added=results.mapNotNull {it.getOrNull()}
                if(token!=epoch)return@launch
                val current=attachments[key].orEmpty()
                require(current.size+added.size<=10) { "每次最多添加 10 个附件。" }
                attachments[key] = current + added
                val failures=results.mapIndexedNotNull {index,result->result.exceptionOrNull()?.let {files[index].name+"："+(it.message?:"读取失败")}}
                if(failures.isNotEmpty())attachmentErrors[key]=failures.joinToString("\n")
                if(added.isNotEmpty())notice="已添加 ${added.size} 个附件，发送后交给 Hermes。"
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { if(token==epoch)attachmentErrors[key] = e.message?:"文件添加失败。" }
            finally {if(token==epoch)attachmentLoads[key]=((attachmentLoads[key]?:1)-1).coerceAtLeast(0)}
        }
    }
    fun chooseFiles(key:String?=if(page==Page.HOME)homeDraftKey()else currentSession?.scopedId) {
        val draftKey=key?:run {notice="先打开一段对话，再添加附件。";return}
        if(filePickerOpen)return
        filePickerOpen=true
        val token=epoch
        scope.launch {
            try {
                // Leave Compose pointer dispatch before entering the native modal event loop.
                yield()
                val files=DesktopFiles.choose()
                if(token==epoch)addFilesToDraft(draftKey,files)
            }catch(e:Exception){if(token==epoch)attachmentErrors[draftKey]=e.message?:"无法打开文件选择器。"}
            finally {filePickerOpen=false}
        }
    }
    fun send(queue:Boolean=false) {
        val s=currentSession ?: run {notice="请先打开一段对话。";return}
        sendToSession(s,queue)
    }
    internal fun sendToSession(s:HermesSession,queue:Boolean=false) {
        if(connectionHealth!="online"){notice="连接尚未恢复，输入与附件已保留。";checkConnection();return}
        if(modelSwitching[s.scopedId]==true){notice="模型正在切换，请稍等。";return}
        if((attachmentLoads[s.scopedId]?:0)>0){notice="附件正在准备，请稍等。";return}
        val text=drafts[s.scopedId].orEmpty().trim();val files=attachments[s.scopedId].orEmpty()
        if(text.isBlank() && files.isEmpty()){notice="写下内容或添加附件后再发送。";return}
        if(demo){notice="布局预览不会向服务器发送消息。";return}
        if(decisions.values.any {it.session.scopedId==s.scopedId}) {
            notice="Hermes 正在等待你选择，请先处理待确认事项。";navigate(Page.TASKS);return
        }
        val run=runs[s.scopedId]
        if(run!=null) {
            if(queue) { queued[s.scopedId]=queued[s.scopedId].orEmpty()+QueuedMessage(session=s,prompt=text,attachments=files);setDraft(s.scopedId,"");attachments[s.scopedId]=emptyList();notice="已加入队列，当前任务完成后发送。";flushCheckpoint() }
            else {
                val runtime=run.controller.runtimeSessionId
                if(runtime==null || run.stopping){error="当前任务还不能追加要求，可以先排队。";return}
                if(files.isNotEmpty()){error="带附件的补充内容请使用排队发送。";return}
                if(steering[s.scopedId]==true)return
                steering[s.scopedId]=true;sendErrors.remove(s.scopedId)
                request(s.profile,{it.steerSession(runtime,text)},finished={steering.remove(s.scopedId)},failed={sendErrors[s.scopedId]=it}) { if(drafts[s.scopedId]?.trim()==text)setDraft(s.scopedId,"");notice="补充指令已送达。" }
            }
            return
        }
        startRun(s,text,files,council=councilMode)
    }
    internal fun startRun(s:HermesSession,text:String,files:List<PendingAttachment>,voiceTurn:Boolean=false,council:String="off",queueId:String?=null) {
        if(connectionHealth!="online"){notice="连接尚未恢复，请恢复后手动继续。";if(voiceTurn)voice.onRunFailure(s.scopedId,"连接尚未恢复");return}
        if(!connected){sendErrors[s.scopedId]="连接已断开，请重新连接网关。";return}
        if(demo || runs.containsKey(s.scopedId))return
        sendErrors.remove(s.scopedId)
        messageReadTokens[s.scopedId]=(messageReadTokens[s.scopedId]?:0)+1;messageLoading[s.scopedId]=false;messageErrors.remove(s.scopedId)
        val token=epoch;val key=s.scopedId;val stream=StreamController();val aid=UUID.randomUUID().toString()
        val needsBaseline=messages[key].isNullOrEmpty()
        val baseline=messages[key].orEmpty().lastOrNull { it.role==MessageRole.ASSISTANT }?.recoverySignature().orEmpty()
        val baselineUser=messages[key].orEmpty().lastOrNull { it.role==MessageRole.USER }?.recoverySignature().orEmpty()
        val moa=modelCatalog.providers.firstOrNull { it.slug.equals("moa",true) || it.slug.contains("mixture-of-agents",true) }
        if(council=="quick" && moa?.models?.firstOrNull()==null){error="服务器尚未提供 MoA 预设，请先配置或选择深度会审。";return}
        val prompt=councilPrompt(text,council)
        val user=ChatMessage(role=MessageRole.USER,content=text+files.joinToString("") { "\n[附件：${it.name}]" })
        val record=RunRecord(s,prompt,text,baseline,baselineUser,aid,user.id,attachments=files,council=council,voice=voiceTurn)
        messages[key]=messages[key].orEmpty()+user+ChatMessage(id=aid,role=MessageRole.ASSISTANT,content="",isStreaming=true)
        runs[key]=DesktopRun(s,stream,aid,record=record);failedSends.remove(key)
        stream.beforeSubmission={runBlocking(Dispatchers.Swing) {
            if(closed || token!=epoch || runs[key]?.assistantId!=aid)throw CancellationException("Turn no longer active")
            saveCheckpoint()
        }}
        if(queueId!=null)queued[key]=queued[key].orEmpty().filterNot { it.id==queueId }
        else { if(drafts[key]?.trim()==text)setDraft(key,"");attachments[key]=attachments[key].orEmpty().filterNot {a->files.any {it.id==a.id}} }
        councilMode="off"
        val api=client(s.profile)
        scope.launch {
            try {
                saveCheckpoint() // Keep the turn and its attachments before any remote submission.
                if(needsBaseline) {
                    val before=withContext(Dispatchers.IO) {api.loadLatestMessages(s)}
                    if(token!=epoch || runs[key]?.assistantId!=aid)return@launch
                    val current=runs.getValue(key)
                    runs[key]=current.copy(record=current.record.copy(baseline=before.lastOrNull {it.role==MessageRole.ASSISTANT}?.recoverySignature().orEmpty(),baselineUser=before.lastOrNull {it.role==MessageRole.USER}?.recoverySignature().orEmpty()))
                    messages[key]=before+messages[key].orEmpty().filter {it.id==user.id || it.id==aid}
                }
                var active=s
                if(council=="quick")active=withContext(Dispatchers.IO) { api.switchSessionModel(s,moa!!.slug,moa.models.first()) }
                if(token!=epoch || runs[key]?.assistantId!=aid)return@launch
                coroutineScope {
                    val events=Channel<StreamEvent>(Channel.UNLIMITED)
                    val consumer=launch {
                        for(event in events)if(token==epoch && runs[key]?.assistantId==aid && event!=StreamEvent.Completed)handleEvent(key,event)
                    }
                    try { withContext(Dispatchers.IO) {
                        val eventSink:(StreamEvent)->Unit={ events.trySend(it);Unit }
                        if(voiceTurn)api.streamVoiceMessage(stream,active,prompt,voicePreferences.fastReply,{ message->scope.launch { if(token==epoch)notice=message } },eventSink,files)
                        else api.streamMessage(stream,active,prompt,files,eventSink)
                    } } finally {events.close();consumer.join()}
                }
                if(token==epoch && runs[key]?.assistantId==aid && !stream.wasDisconnected() && !stream.isStopped())finishRun(key,failed=runs[key]?.failed==true)
            } catch(e:CancellationException){throw e}
            catch(e:Exception){if(token==epoch && runs[key]?.assistantId==aid) {
                if(!stream.submissionAttempted || stream.submissionRejected) {
                    runs.remove(key);messages[key]=messages[key].orEmpty().filterNot { it.id==user.id || it.id==aid }
                    restoreUnsent(key,DraftRecord(text,files));failedSends[key]=DraftRecord(text,files)
                } else runs[key]?.let { runs[key]=it.copy(recovering=true,status="连接中断，正在取回回复") }
                if(voiceTurn)voice.onRunFailure(s.scopedId,e.message ?: "回复失败")
                sendErrors[key]=e.message ?: "发送失败，内容已保留。"
                if(e.message.orEmpty().contains("busy",true)||e.message.orEmpty().contains("正在运行")) {
                    sendErrors[key]="服务器中的上一个任务仍在运行，正在同步待确认事项；本次输入已保留。"
                    refreshAgentState(s)
                }
            }}
        }
    }
    private fun restoreUnsent(key:String,value:DraftRecord) {
        setDraft(key,listOf(value.text,drafts[key].orEmpty()).filter(String::isNotBlank).distinct().joinToString("\n\n"))
        attachments[key]=(value.attachments+attachments[key].orEmpty()).distinctBy { it.id }
    }
    private fun patchAssistant(key:String,transform:(ChatMessage)->ChatMessage) {
        val id=runs[key]?.assistantId ?: return
        messages[key]=messages[key].orEmpty().map { if(it.id==id)transform(it) else it }
    }
    private fun handleEvent(key:String,event:StreamEvent) {
        val old=runs[key] ?: return
        val run=old.copy(lastEvent=System.currentTimeMillis(),record=old.record.copy(runtimeId=old.controller.runtimeSessionId,attempted=old.controller.submissionAttempted))
        runs[key]=run
        when(event) {
            is StreamEvent.AssistantDelta -> patchAssistant(key) { it.copy(content=it.content+event.text) }
            is StreamEvent.AssistantInterim -> patchAssistant(key) { it.copy(content=mergeInterimAssistantText(it.content,event.content)) }
            is StreamEvent.AssistantCompleted -> patchAssistant(key) { it.copy(content=mergeCompletedAssistantText(it.content,event.content,event.responsePreviewed),isStreaming=false) }
            is StreamEvent.ReasoningDelta -> patchAssistant(key) { it.copy(reasoning=it.reasoning+event.text) }
            is StreamEvent.ReasoningAvailable -> patchAssistant(key) { it.copy(reasoning=event.text) }
            is StreamEvent.RunStarted -> runs[key]=run.copy(status="正在处理")
            is StreamEvent.ToolStarted -> {
                runs[key]=run.copy(status="正在执行 ${event.name}",tools=run.tools+ToolActivity(name=event.name,preview=event.preview,status=ToolStatus.RUNNING))
                if(event.name in setOf("clarify","approval"))scope.launch {
                    delay(1200)
                    if(runs[key]?.assistantId==run.assistantId&&decisions.values.none {it.session.scopedId==key})refreshAgentState(run.session)
                }
            }
            is StreamEvent.ToolProgress -> runs[key]=run.copy(status=event.name,tools=run.tools.map { if(it.name==event.name && it.status==ToolStatus.RUNNING)it.copy(preview=event.preview)else it })
            is StreamEvent.ToolCompleted -> runs[key]=run.copy(tools=run.tools.map { if(it.name==event.name && it.status==ToolStatus.RUNNING)it.copy(preview=event.preview,status=ToolStatus.COMPLETED)else it })
            is StreamEvent.ToolFailed -> runs[key]=run.copy(tools=run.tools.map { if(it.name==event.name && it.status==ToolStatus.RUNNING)it.copy(preview=event.preview,status=ToolStatus.FAILED)else it })
            is StreamEvent.AgentRequestPending,is StreamEvent.AgentRequestExpired -> receiveAgentControl(run.session,event)
            is StreamEvent.ConnectionInterrupted -> runs[key]=run.copy(recovering=true,status="连接中断，正在取回回复")
            is StreamEvent.Error -> { sendErrors[key]=event.message;runs[key]=run.copy(failed=true,status=event.message);voice.onFailure(event.message) }
            StreamEvent.Completed -> finishRun(key,failed=run.failed)
        }
    }
    private fun finishRun(key:String,cancelled:Boolean=false,failed:Boolean=false) {
        val run=runs.remove(key) ?: return
        if(run.tools.isNotEmpty())completedActivities[run.assistantId]=run.tools
        run.controller.finish();messages[key]=messages[key].orEmpty().map { if(it.id==run.assistantId)it.copy(isStreaming=false)else it }
        decisions.entries.removeAll { it.value.session.scopedId==key }
        val reply=messages[key].orEmpty().lastOrNull { it.role==MessageRole.ASSISTANT }?.content.orEmpty()
        if(!cancelled && !failed) {
            completions=(listOf(RunCompletionSummary(key,run.session.title,reply.take(300),ChatInsightParser.artifactsFromText(reply)))+completions).take(100)
            indexArtifacts(run.session,messages[key].orEmpty())
            if(!appFocused || page!=Page.CHAT || currentSession?.scopedId!=key) {
                if(key !in unread)unread+=key
                if(notifications && notificationMessages)DesktopNotifications.show(run.session.title,"Hermes 已完成回复",notificationSound)
            }
        }
        request(run.session.profile,{it.sessionForProfile(run.session.id,run.session.profile)}) { updated->
            if(updated!=null && updated.profile==profile) { sessions=(listOf(updated)+sessions.filterNot { it.id==updated.id });if(currentSession?.scopedId==key)currentSession=updated }
        }
        val next=queued[key]?.firstOrNull()
        // Restored turns synchronize first; their saved queues require an explicit Continue.
        if(next!=null && !cancelled && !failed && !run.recovering)startRun(next.session,next.prompt,next.attachments,queueId=next.id)
        else if(run.record.voice && !cancelled && !failed)voice.onReply(key,reply)
        else if(cancelled || failed)voice.onRunFailure(key,if(cancelled)"已停止" else "任务失败")
    }
    internal fun reconcileRuns(force:Boolean=false) {
        runs.values.filter { force || it.recovering || it.status.contains("clarify") || it.status.contains("approval") || decisions.values.any {d->d.session.scopedId==it.session.scopedId} || System.currentTimeMillis()-it.lastEvent>45_000 }.forEach { run->
            val key=run.session.scopedId
            if(key in polling || key in agentSyncing || run.stopping)return@forEach
            polling+=key
            request(run.session.profile,{ it.resumeSession(run.session) },finished={polling.remove(key)},failed={ /* retain state and retry on next tick */ }) { snapshot->
                val latest=snapshot.messages
                val current=runs[key] ?: return@request
                if(current.assistantId!=run.assistantId)return@request
                synchronizeDecisions(snapshot)
                snapshot.pendingRequests.forEach {receiveAgentControl(snapshot.session,StreamEvent.AgentRequestPending(it))}
                if(snapshot.running==true||snapshot.pendingRequests.isNotEmpty()) {
                    current.controller.runtimeSessionId=snapshot.session.runtimeId
                    if(current.recovering&&latest.isNotEmpty())messages[key]=latest
                    runs[key]=current.copy(record=current.record.copy(runtimeId=snapshot.session.runtimeId),candidate="",
                        status=if(snapshot.pendingRequests.isNotEmpty())"等待你的处理"else"服务器正在处理")
                    return@request
                }
                val candidate=recoveredReply(latest,current.record)
                if(candidate!=null) {
                    val signature=candidate.recoverySignature()
                    if(current.candidate==signature && System.currentTimeMillis()-current.candidateAt>=4_000) {
                        messages[key]=latest;runs[key]=current.copy(recovering=true);finishRun(key)
                    } else runs[key]=current.copy(candidate=signature,candidateAt=System.currentTimeMillis(),status="正在核对服务器回复")
                } else if(System.currentTimeMillis()-current.started>15*60_000) runs[key]=current.copy(recovering=true,status="暂未取回完整结果，可继续等待或停止")
                else if(current.recovering) { messages[key]=latest;runs[key]=current.copy(candidate="",status="正在恢复上次的任务") }
            }
        }
    }
    fun resumeQueue(s:HermesSession) { if(runs.containsKey(s.scopedId))return;queued[s.scopedId]?.firstOrNull()?.let { startRun(s,it.prompt,it.attachments,queueId=it.id) } }
    fun removeQueued(key:String,id:String,restore:Boolean=false) { val item=queued[key]?.firstOrNull { it.id==id } ?: return;queued[key]=queued[key].orEmpty().filterNot { it.id==id };if(restore)restoreUnsent(key,DraftRecord(item.prompt,item.attachments)) }
    fun stop(s:HermesSession,onStopped:()->Unit={}) {
        val run=runs[s.scopedId] ?: return
        voice.pause();runs[s.scopedId]=run.copy(stopping=true,status="正在停止",record=run.record.copy(stopping=true))
        val runtime=run.controller.runtimeSessionId ?: run.record.runtimeId
        if(runtime==null && !run.controller.submissionAttempted) { run.controller.stop();finishRun(s.scopedId,cancelled=true);onStopped();return }
        if(runtime==null){runs[s.scopedId]=run.copy(status="尚未取得运行标识，请稍后重试停止");return}
        request(s.profile,{it.stopRunChecked(runtime,s.profile)},finished={runs[s.scopedId]?.let { current->if(current.stopping)runs[s.scopedId]=current.copy(stopping=false,status="停止尚未确认，请重试") }}) {
            run.controller.stop();finishRun(s.scopedId,cancelled=true);onStopped()
        }
    }
    fun respond(value:PendingDecision,answer:String,answers:Map<String,String> = emptyMap()) {
        val entry=decisions.entries.firstOrNull {it.value.session.scopedId==value.session.scopedId&&it.value.request.requestId==value.request.requestId}?:return
        if(entry.value.request.isResponding)return
        val key=value.session.scopedId
        val hadRun=runs.containsKey(key)
        decisions[entry.key]=entry.value.copy(request=entry.value.request.copy(isResponding=true))
        request(value.profile,{it.respondAgentRequest(entry.value.request,answer,answers)},finished={decisions[entry.key]?.let {decisions[entry.key]=it.copy(request=it.request.copy(isResponding=false))}}) {
            decisions.remove(entry.key)
            runs[key]?.let {runs[key]=it.copy(status="正在继续处理",lastEvent=System.currentTimeMillis(),candidate="")}
            if(!hadRun&&!runs.containsKey(key))refreshAgentState(value.session)
            notice=if(value.request.type==AgentRequestType.ACTION_REQUIRED)"已告知服务器当前终端无法处理此请求。"else"回答已提交，Hermes 将继续处理。"
        }
    }
    fun dismissDecision(key:String) { decisions.remove(key) }
    private fun persistDecisions() = Unit
    private fun restoreDecisions() {
        runCatching {
            DecisionCodec.decode(store.get(storageKey("decisions"), "[]")).filter { !decisions.containsKey("${it.session.scopedId}:${it.request.requestId}") }.forEach { value ->
                decisions["${value.session.scopedId}:${value.request.requestId}"] = value
            }
            if(store.get(storageKey("decisions"),"[]")!="[]") {
                repository?.save(checkpoint(++revision))
                store.put(storageKey("decisions"),"[]")
            }
        }.onFailure { error = "待确认事项恢复失败：${it.message}" }
    }
    fun navigate(target:Page) {
        val flush=flushEditor
        if(flush!=null)flush {page=target}else page=target
    }
    fun toggleFileBrowser() {
        assistantPanel=false
        if(documentSplit){documentSplit=false;setFilesPanel(true);return}
        if(page in listOf(Page.CHAT,Page.FILES))setFilesPanel(!filesPanelOpen)
        else {setFilesPanel(true);navigate(if(currentSession!=null)Page.CHAT else Page.FILES)}
    }
    fun setFilesPanel(open:Boolean) {
        filesPanelOpen=open;savePreference("filesPanelOpen",open.toString())
    }
    fun browse(path:String?=project?.primaryPath,navigate:Boolean=true) {
        if(navigate){setFilesPanel(true);assistantPanel=false;if(page !in listOf(Page.CHAT,Page.FILES))this.navigate(Page.FILES)}
        if(demo)return
        val p=profile;val token=++browseToken
        filesLoading=true;filesLoadError=null;requestedFilesPath=path
        request(p,{ if(path==null) it.initialWorkspace() else it.listWorkspace(path) },finished={if(token==browseToken)filesLoading=false},failed={if(token==browseToken&&profile==p)filesLoadError=it}) {
            if(profile==p && token==browseToken) listing=it
        }
    }
    fun openDocument(path:String, source:HermesSession?=currentSession, p:String=profile) {
        
        val token=++documentReadToken
        val origin=source?.takeIf { it.profile==p }
        val raw=normalizeArtifactTarget(path, markdownLink=true)
        val root=origin?.workspacePath ?: listing?.path ?: project?.primaryPath.orEmpty()
        val recent=RecentArtifact(p,origin?.id.orEmpty(),origin?.title.orEmpty(),path=raw,name=raw.substringAfterLast('/'),kind="document",workspacePath=root)
        val cached=origin?.let { messages[it.scopedId] }.orEmpty()
        documentLoading=true
        request(p,{ ArtifactFileReader(it).read(recent,origin,cached) },finished={if(token==documentReadToken)documentLoading=false}) {
            if(profile==p && token==documentReadToken) {
                val existing=openDocuments["$p:${it.path}"]
                selectDocument(if(existing!=null && edits[documentKey(existing)]?.let { text->text!=existing.document.content }==true)existing else DocumentTab(it,p,origin))
            }
        }
    }
    fun openProfileFile(file:HermesProfileFile) {
        
        val p=profile;val token=++documentReadToken
        request(p,{ it.readProfileFile(file) }) {
            if(profile==p && token==documentReadToken) { page=Page.FILES; selectDocument(DocumentTab(it,p)); editing=false }
        }
    }
    fun documentKey(tab:DocumentTab)= "${tab.profile}:${tab.document.path}"
    fun documentText():String = document?.let { edits[documentKey(it)] ?: it.document.content }.orEmpty()
    fun isDirty()=document?.let { edits[documentKey(it)]?.let { e -> e!=it.document.content } } ?: false
    fun discardEdit() { document?.let { edits.remove(documentKey(it)) }; editing=false }
    fun saveWithEditor() { flushEditor?.invoke { saveDocument() } ?: saveDocument() }
    fun saveDocument() { val tab=document ?: return; if(savingDocument)return; val text=documentText(); savingDocument=true; request(tab.profile,{ api ->
        val live=api.readWorkspaceDocumentForProfile(tab.document.path,tab.profile)
        check(live.content==tab.document.content) { "服务器上的文件已被修改。为避免覆盖，请先将当前内容另存到本机，再重新打开。" }
        api.saveWorkspaceDocumentForProfile(tab.document.path,text,tab.profile,tab.document.mimeType)
    }, finished={savingDocument=false}) { updated ->
        val key=documentKey(tab);openDocuments[key]=tab.copy(document=updated)
        if(document?.let(::documentKey)==key)document=tab.copy(document=updated)
        if(edits[key]==text)edits.remove(key)
        notice="已保存"
    } }
    fun attachDocument() { if(isDirty()){error="请先保存文档，再添加到对话。";return}; val tab=document ?: return; val s=currentSession ?: run { error="请先打开一段对话。"; return }; if(s.profile!=tab.profile) { error="请选择同一工作空间中的对话。"; return }; attachments[s.scopedId]=attachments[s.scopedId].orEmpty()+DesktopFiles.remoteAttachment(tab.document); page=Page.CHAT }
    fun loadSettings() {
        val p=profile;navigate(Page.PROFILE);settingsLoading=true;settingsError=null
        if(settingsSection=="模型")loadModelCatalog()
        request(p,{it.serverSettings()},finished={if(p==profile)settingsLoading=false},failed={if(p==profile)settingsError=it}){if(p==profile)settings=it}
        request(p,{it.listSkills()},failed={if(p==profile)settingsError=it}){if(p==profile)skills=it}
        request(p,{it.listToolsets()},failed={if(p==profile)settingsError=it}){if(p==profile)toolsets=it}
        request(p,{it.listMcpServers()},failed={if(p==profile)settingsError=it}){if(p==profile)mcpServers=it}
    }
    fun submitDraft() {
        val s=currentSession?:run {notice="请先打开一段对话。";return}
        if(runs[s.scopedId]==null){send();return}
        when(runningSendMode) {
            "steer"->send()
            "interrupt"->{
                val text=drafts[s.scopedId].orEmpty().trim();val files=attachments[s.scopedId].orEmpty()
                if((text.isBlank()&&files.isEmpty())||(attachmentLoads[s.scopedId]?:0)>0||runs[s.scopedId]?.stopping==true)return
                stop(s){startRun(s,text,files)}
            }
            else->send(queue=true)
        }
    }
    fun selectModel(s:HermesSession,provider:String,model:String) {
        val key=s.scopedId
        if(modelCatalogError?.restartRequired==true){modelSwitchErrors[key]=modelCatalogError!!.guidance;return}
        if(s.provider==provider&&s.model==model)return
        if(modelSwitching[key]==true||runs.containsKey(key)){notice="请在当前任务结束后切换模型。";return}
        modelGenerations[key]=(modelGenerations[key]?:0L)+1
        modelSwitching[key]=true;modelSwitchErrors.remove(key)
        request(s.profile,{it.switchSessionModel(s,provider,model)},finished={modelSwitching.remove(key)},failed={
            val problem=modelCatalogProblem(it)
            modelSwitchErrors[key]=if(problem.restartRequired)problem.guidance else it
            if(s.profile==profile&&problem.restartRequired)modelCatalogError=problem
        }) {updated->
            // The user may have switched conversations while the server changed this model.
            sessions=sessions.map {if(it.scopedId==key)updated else it}
            if(currentSession?.scopedId==key)currentSession=updated
            notice=if(updated.model==model)"已切换模型 · ${updated.model}"else"服务器当前模型为 ${updated.model}，未确认切换到 $model。"
            if(updated.profile==profile)loadModelCatalog(force=true)
        }
    }
    fun loadModelCatalog(force:Boolean=false) {
        if(demo||!connected||modelCatalogLoading)return
        if(!force&&modelCatalogError==null&&modelCatalogLoadedAt?.let {System.currentTimeMillis()-it<60_000}==true)return
        val p=profile;val token=++modelCatalogToken
        modelCatalogLoading=true
        request(p,{it.modelCatalog()},finished={if(p==profile&&token==modelCatalogToken)modelCatalogLoading=false},
            failed={if(p==profile&&token==modelCatalogToken)modelCatalogError=modelCatalogProblem(it)}) {
            if(p==profile&&token==modelCatalogToken){modelCatalog=it;modelCatalogError=null;modelCatalogLoadedAt=System.currentTimeMillis()}
        }
    }
    fun setSendMode(enter:Boolean){sendOnEnter=enter;savePreference("sendOnEnter",enter.toString())}
    fun rememberReading(key:String,value:ReadingPosition){readingPositions[key]=value}

    fun savePreference(key:String,value:String) { if(!demo) try { store.put(key,value) } catch(e:Exception) { error="设置保存失败：${e.message}" } }
    fun search(query:String) {
        searchJob?.cancel(); searchResults=null;searchBusy=false;searchError=null
        if(query.isBlank()) return
        searchBusy=true;searchResults=emptyList()
        val p=profile; val token=epoch; val api=if(!demo) client(p) else null; val list=(if(showArchived)archived else sessions).take(100)
        searchJob=scope.launch {
            delay(250)
            try {
            val found=mutableListOf<SessionSearchResult>()
            var failures=0
            for(s in list) { ensureActive(); if(s.title.contains(query,true)||s.preview.contains(query,true)) found+=SessionSearchResult(s,s.preview)
                else if(api!=null) {
                    val ms=try {withContext(Dispatchers.IO) {api.loadRecentMessagePage(s,200).messages}}catch(e:CancellationException){throw e}catch(e:Exception){failures++;emptyList()}
                    val m=ms.lastOrNull { it.content.contains(query,true) }; if(m!=null) {val pos=m.content.indexOf(query,ignoreCase=true);found+=SessionSearchResult(s,m.content.substring((pos-35).coerceAtLeast(0)).take(160),m.id,true)}
                }
                if(token==epoch && profile==p) searchResults=found.toList()
            }
            if(failures>0&&token==epoch&&profile==p)searchError="$failures 段会话暂时无法读取，搜索结果可能不完整。"
            } finally {if(isActive && token==epoch && profile==p)searchBusy=false}
        }
    }
    fun toggleRecording() { if(recording)voice.stopCapture() else voice.startCapture(false) }
    fun speak(text:String,restart:Boolean=false) { voice.speak(text,restart) }
    fun close():Boolean {
        if(closed)return true
        companion.reset()
        voice.close();if(!flushCheckpoint())return false;closed=true;workspaceReady=false
        clients.values.forEach { it.close() };scope.cancel()
        return true
    }
    private fun checkpoint(number:Long):WorkspaceState {
        val keys=drafts.keys+attachments.keys
        val docs=openDocuments.values.mapNotNull { tab -> edits[documentKey(tab)]?.takeIf { it!=tab.document.content }?.let { text->EditedDocument(tab.profile,tab.document.path,tab.document.name,tab.document.mimeType,tab.document.content,text,tab.sourceSession) } }
        return WorkspaceState(number,keys.associateWith { DraftRecord(drafts[it].orEmpty(),attachments[it].orEmpty()) },queued.toMap(),
            runs.values.map { it.record.copy(runtimeId=it.controller.runtimeSessionId ?: it.record.runtimeId,attempted=it.controller.submissionAttempted || it.record.attempted,stopping=it.stopping) },
            unread.toSet(),docs,recentArtifacts.toList(),voiceNotes.toList(),decisions.values.map { it.copy(request=it.request.copy(isResponding=false)) },completions,currentSession,indexedSessionVersions.toMap(),sessionSummaries.toMap())
    }
    internal suspend fun saveCheckpoint() { if(demo || !workspaceReady)return;val next=checkpoint(++revision);val repo=repository;withContext(Dispatchers.IO) { repo?.save(next) } }
    fun flushCheckpoint():Boolean { if(demo || !workspaceReady)return true;return try {settingsDrafts.values.forEach {it.persist()}; repository?.save(checkpoint(++revision));true } catch(e:Exception) { error="本机内容保存失败：${e.message}";false } }
    internal fun restoreWorkspace(saved:WorkspaceState) {
        revision=saved.revision
        saved.drafts.forEach { (key,value) -> drafts[key]=value.text;attachments[key]=value.attachments }
        queued.putAll(saved.queues);unread+=saved.unread;recentArtifacts+=saved.artifacts.map {if(saved.indexVersions.isEmpty())it.copy(seenAtMillis=0)else it};voiceNotes+=saved.voice
        saved.documents.forEach { d -> val tab=DocumentTab(WorkspaceDocument(d.name,d.path,d.mime,d.baseline),d.profile,d.source);openDocuments[documentKey(tab)]=tab;edits[documentKey(tab)]=d.text }
        saved.decisions.forEach { decisions["${it.session.scopedId}:${it.request.requestId}"]=it }
        completions=saved.completions
        indexedSessionVersions.putAll(saved.indexVersions);sessionSummaries.putAll(saved.summaries)
        saved.runs.forEach { r ->
            val control=StreamController().also { it.runtimeSessionId=r.runtimeId;it.submissionAttempted=r.attempted }
            runs[r.session.scopedId]=DesktopRun(r.session,control,r.assistantId,r.started,"正在恢复上次的任务",record=r,recovering=true)
        }
    }
    private fun loadLocalPreferences() {
        fun value(key:String,default:String="")=store.get(key,default)
        userAvatar=value("userAvatar");hermesAvatar=value("hermesAvatar");hermesName=value("hermesName","Hermes");biography=value("biography","个人工作助理")
        language=value("language","system");setDesktopLanguage(language)
        reduceMotion=value("reduceMotion","false")=="true"
        floatingAssistantEnabled=value("floatingAssistantEnabled","true")=="true"
        sidebarCollapsed=value("sidebarCollapsed","false")=="true"
        compact=value("compact","true")=="true";textScale=(value("textScale","1").toFloatOrNull()?:1f).coerceIn(.9f,1.3f)
        panelWidth=(value("panelWidth","328").toFloatOrNull()?:328f).coerceIn(260f,580f)
        activityMode=value("activityMode","compact").takeIf {it in listOf("compact","expanded","answer")}?:"compact"
        runningSendMode=value("runningSendMode","queue").takeIf {it in listOf("queue","steer","interrupt")}?:"queue"
        showOutline=value("showOutline","false")=="true"
        sendOnEnter=value("sendOnEnter","false")=="true";showHiddenFiles=value("showHiddenFiles","false")=="true"
        notificationMessages=value("notificationMessages","true")=="true";notificationTasks=value("notificationTasks","true")=="true"
        notificationSound=value("notificationSound","true")=="true";notificationBadge=value("notificationBadge","true")=="true"
        value("snippets").takeIf(String::isNotBlank)?.let { raw->snippets=JSONArray(raw).objects().map { PromptSnippet(it.getString("id"),it.getString("title"),it.getString("text")) } }
        val v=JSONObject(value("voicePreferences","{}"))
        voicePreferences=VoicePreferences(enabled=v.optBoolean("enabled",true),language=v.optString("language","zh-CN"),transcriptScript=v.optString("script","simplified"),autoSend=v.optBoolean("autoSend",false),engine=v.optString("engine","automatic"),autoRead=v.optBoolean("autoRead",true),continuous=v.optBoolean("continuous",true),fastReply=v.optBoolean("fastReply",true),noiseSensitivity=v.optString("noise","balanced"),speechRate=v.optDouble("rate",1.0).toFloat())
    }
    fun saveVoicePreferences(value:VoicePreferences) {
        voicePreferences=value
        savePreference("voicePreferences",JSONObject().put("enabled",value.enabled).put("language",value.language).put("script",value.transcriptScript).put("autoSend",value.autoSend).put("engine",value.engine).put("autoRead",value.autoRead).put("continuous",value.continuous).put("fastReply",value.fastReply).put("noise",value.noiseSensitivity).put("rate",value.speechRate).toString())
    }
    fun saveSnippets(values:List<PromptSnippet>) { snippets=values;savePreference("snippets",JSONArray().apply { values.forEach { put(JSONObject().put("id",it.id).put("title",it.title).put("text",it.text)) } }.toString()) }
    fun changeLanguage(value:String) { language=value;setDesktopLanguage(value);savePreference("language",value) }
    fun indexArtifacts(s:HermesSession,values:List<ChatMessage>) {
        val found=values.filter { it.role==MessageRole.ASSISTANT }.flatMap { message -> ChatInsightParser.artifactsFromText(message.content).map { a->RecentArtifact(s.profile,s.id,s.title,message.id,a.path,a.name,a.kind,s.workspacePath,parseDesktopInstant(message.createdAt)?.toEpochMilli()?:parseDesktopInstant(s.updatedAt)?.toEpochMilli()?:0L) } }
        sessionSummaries[s.scopedId]=sessionSummary(values,s.preview)
        if(found.isNotEmpty()) {
            val merged=mergeRecentArtifacts(recentArtifacts.toList(),found)
            recentArtifacts.clear();recentArtifacts+=merged
        }
    }
    fun syncArtifactIndex(force:Boolean=false) {
        if(demo||!connected||artifactIndexJob?.isActive==true)return
        val p=profile;val token=epoch;val api=client(p)
        val candidates=sessions.filter {force||indexedSessionVersions[it.scopedId]!="${it.updatedAt}:${it.messageCount}"}
        if(candidates.isEmpty())return
        artifactsIndexing=true;artifactsIndexError=null
        artifactIndexJob=scope.launch {
            var failures=0
            try {candidates.forEachIndexed {index,s->
                ensureActive();if(p!=profile||token!=epoch)return@launch
                artifactsIndexProgress="正在整理 ${index+1} / ${candidates.size} 段会话"
                try {
                    var offset=0;var total=s.messageCount;val collected=mutableListOf<ChatMessage>()
                    do {val page=withContext(Dispatchers.IO){api.loadMessagePage(s,200,offset)}
                        ensureActive();if(p!=profile||token!=epoch)return@launch
                        collected+=page.messages;offset+=200;total=maxOf(total,page.totalCount)
                        if(page.messages.isEmpty())break
                    }while(offset<total)
                    indexArtifacts(s,collected)
                    indexedSessionVersions[s.scopedId]="${s.updatedAt}:${s.messageCount}"
                }catch(e:CancellationException){throw e}catch(_:Exception){failures++}
                delay(60)
            }
            if(failures>0)artifactsIndexError="$failures 段会话暂未整理完成，可以重试。"
            }finally {if(p==profile&&token==epoch){artifactsIndexing=false;artifactsIndexProgress=""}}
        }
    }
    fun showDetails(title:String,text:String) {detailsTitle=title;detailsText=text}
    fun showRecentFiles() {fileSection="最近产物";assistantPanel=false;setFilesPanel(true);navigate(if(currentSession!=null)Page.CHAT else Page.FILES)}
    fun discussDocument(quote:String="") {
        val tab=document?:return
        if(isDirty()){error="文档有未保存修改，请先保存后再讨论。";return}
        fun attach(s:HermesSession) {
            val existing=attachments[s.scopedId].orEmpty()
            if(existing.none {it.remotePath==tab.document.path})attachments[s.scopedId]=existing+DesktopFiles.remoteAttachment(tab.document)
            val hint=if(quote.isBlank())"请基于《${tab.document.name}》"else "关于《${tab.document.name}》中的这段内容：\n> ${quote.replace("\n","\n> ")}\n\n"
            val draft=drafts[s.scopedId].orEmpty();drafts[s.scopedId]=if(draft.isBlank())hint else "$draft\n\n$hint"
            document=tab;documentSplit=true;page=Page.CHAT
        }
        val session=currentSession?.takeIf {it.profile==tab.profile}
        if(session!=null)attach(session)else newSession(onOpened=::attach)
    }
    fun openArtifact(value:RecentArtifact) {
        val s=HermesSession(value.sessionId,value.sessionTitle,workspacePath=value.workspacePath,profile=value.profile)
        if(profile!=value.profile)loadProfile(value.profile)
        openDocument(value.sourcePath.ifBlank { value.path },s,value.profile)
    }
    fun selectDocument(tab:DocumentTab) {
        val flush=flushEditor
        if(flush!=null)flush { applyDocumentSelection(tab) }else applyDocumentSelection(tab)
    }
    private fun applyDocumentSelection(tab:DocumentTab) {
        val key=documentKey(tab);val existing=openDocuments[key]
        val selected=if(existing!=null && edits[key]?.let {it!=existing.document.content}==true)existing else tab
        document=selected;openDocuments[key]=selected;editing=false
        if(!(documentSplit&&currentSession!=null&&page==Page.CHAT))page=Page.FILES
    }
    fun closeDocument(tab:DocumentTab,discard:Boolean=false) {
        if(discard)edits.remove(documentKey(tab))
        val key=documentKey(tab)
        if(discard || edits[key]?.let {it!=tab.document.content}!=true)openDocuments.remove(key)
        if(key==document?.let(::documentKey))document=openDocuments.values.lastOrNull { it.profile==profile && documentKey(it)!=key }
        editing=false
    }
    fun hasUnsavedChanges()=openDocuments.values.any { tab->edits[documentKey(tab)]?.let { it!=tab.document.content }==true }
    fun setDocumentText(tab:DocumentTab,text:String) { edits[documentKey(tab)]=text;openDocuments[documentKey(tab)]=tab }
    fun reopenDraft(key:String) { openDocuments[key]?.let { document=it;page=Page.FILES;editing=true } }
    fun copyDocumentPath() { document?.let { DesktopFiles.copy(it.document.path) } }
    fun retryRecovery() { reconcileRuns(force=true) }
    fun startAgentUpdate() {
        if(demo || updating)return
        if(runs.isNotEmpty()){error="请先结束正在运行的任务，再更新服务器。";return}
        updating=true;val p=profile;val token=epoch;val api=client(p)
        scope.launch {
            try {
                updateProgress=withContext(Dispatchers.IO) { api.startAgentUpdate() }
                repeat(120) {
                    delay(2_000);if(token!=epoch)return@launch
                    val progress=withContext(Dispatchers.IO) { runCatching { api.agentUpdateStatus() }.getOrNull() }
                    if(progress!=null) { updateProgress=progress
                        if(!progress.running) {
                            if(progress.exitCode!=null && progress.exitCode!=0)error="服务器更新失败，请查看日志。"
                            else { withContext(Dispatchers.IO){api.reconnectGateway()};gateway=withContext(Dispatchers.IO){api.gatewayInfo()};notice="更新流程已结束，网关连接已恢复。" }
                            return@launch
                        }
                    }
                }
                error="暂未取得更新最终状态，可以重新检查；不会重复启动更新。"
            } catch(e:Exception){error=e.message}finally{updating=false}
        }
    }
    fun diagnoseConnection() {
        if(diagnosticBusy)return
        diagnosticLines=emptyList();diagnosticDetails.clear();diagnosticBusy=true
        val p=profile
        request(block={ api ->
            val checks=listOf<Pair<String,()->String>>(
                "HTTP 接口" to {api.checkGatewayAccess();"网关接口可以访问"},
                "登录状态" to {api.checkSavedSession();"当前登录状态有效"},
                "实时连接" to {api.ensureConnected();"实时通道已建立"},
                "会话读取" to {val rows=api.listSessions();"已读取 ${rows.sessions.size} 段会话（本次诊断）"},
                "文件能力" to {api.initialWorkspace();"文件目录可以读取"})
            checks.map { (name,check)->
                val result=runCatching(check)
                Triple(name,result.isSuccess,result.getOrElse {it.message?:"请求失败，请重试"}.take(800))
            }
        },finished={diagnosticBusy=false},failed={message->if(profile==p){diagnosticLines=listOf("连接诊断" to false);diagnosticDetails["连接诊断"]=message}}) { results ->
            if(profile==p){diagnosticLines=results.map {it.first to it.second};results.forEach {diagnosticDetails[it.first]=it.third}}
        }
    }

    private fun seedDemo() {
        connected=true; profiles=listOf(HermesProfile("default")); project=HermesProject("desktop","Hermes 桌面版","/workspace/hermes-desktop"); projects=listOf(project!!,HermesProject("content","内容创作","/workspace/content"))
        val s=HermesSession("design","桌面版功能规划",preview="先看看核心页面的布局和交互",workspacePath=project!!.primaryPath,profile="default")
        sessions=listOf(s,HermesSession("release","整理产品更新说明",preview="已整理好本次升级的重点内容"),HermesSession("daily","日常助理",preview="今天想从哪件事开始？")); currentSession=s
        messages[s.scopedId]=listOf(ChatMessage(role=MessageRole.USER,content="帮我把桌面版的核心功能整理一下，生成一份可以继续修改的清单。"),ChatMessage(role=MessageRole.ASSISTANT,content="我按使用场景整理好了，建议保留这三组能力：\n\n## 日常对话\n会话、语音、附件与运行中追加要求。\n\n## 任务处理\n集中查看审批、运行任务和定时任务。\n\n## 文件工作区\n查看产物、预览文档，并继续编辑。\n\n完整清单：[功能清单.md](/workspace/hermes-desktop/功能清单.md)"))
        document=DocumentTab(WorkspaceDocument("功能清单.md","/workspace/hermes-desktop/功能清单.md","text/markdown","# 桌面版功能清单\n\n## 目标\n延续移动端的核心能力，让对话、任务和文件在电脑上更顺手。\n\n## 核心页面\n\n1. **助理首页**：继续最近的对话，查看待确认事项。\n2. **聊天工作台**：边聊边看文档，随时补充要求。\n3. **任务中心**：集中处理审批、运行任务与定时任务。\n4. **文件工作区**：浏览、预览、编辑和保存项目文档。\n\n## 桌面交互\n支持快捷键、右键菜单和文件拖拽。"),"default",s)
        listing=WorkspaceListing("Hermes 桌面版","/workspace/hermes-desktop",entries=listOf(WorkspaceEntry("功能清单.md","/workspace/hermes-desktop/功能清单.md",false,4096),WorkspaceEntry("产品更新说明.md","/workspace/hermes-desktop/产品更新说明.md",false,6144),WorkspaceEntry("设计素材","/workspace/hermes-desktop/assets",true)))
        val req=AgentRequest("approval","runtime",s.id,AgentRequestType.APPROVAL,"保存桌面版功能清单","新建文档 / 功能清单.md",listOf(AgentRequestChoice("允许一次","once"),AgentRequestChoice("拒绝","deny")))
        decisions["demo"]=PendingDecision("default",s,req)
        settings=ServerSettings(); gateway=GatewayInfo("演示","演示")
        document?.let { openDocuments[documentKey(it)]=it };indexArtifacts(s,messages[s.scopedId].orEmpty())
    }
}
