package com.qingyu.hermescompanion.desktop

import com.qingyu.hermescompanion.platform.DesktopHost

import androidx.compose.foundation.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.font.*
import androidx.compose.ui.unit.*
import com.qingyu.hermescompanion.model.*
import com.qingyu.hermescompanion.BuildConfig
import com.qingyu.hermescompanion.data.HermesApiClient

private val settingsGroups=linkedMapOf("日常使用" to listOf("外观与账户","桌面助手","对话与记忆","语音","通知","提示词片段"),"AI 能力" to listOf("模型","模型供应商","Skills","工具集","MCP"),"连接与帮助" to listOf("网关","帮助"))
private val settingsKeywords=mapOf("桌面助手" to "悬浮球 后台 托盘 截图 长按 置顶", "外观与账户" to "主题 皮肤 深色 语言 字号 密度 昵称 头像", "模型" to "服务商 默认模型 推理 辅助 备用 会审", "模型供应商" to "API Key 密钥 服务商 提供商 地址 端点 provider", "对话与记忆" to "人格 时区 记忆 审批 压缩 上下文 发送 换行", "语音" to "麦克风 识别 朗读 音色 转写 录音", "通知" to "消息 提醒 托盘", "提示词片段" to "模板 快捷输入", "Skills" to "技能 扩展", "工具集" to "浏览器 文件 搜索 终端", "MCP" to "服务 外部工具", "网关" to "登录 连接 诊断 更新", "帮助" to "快捷键 版本 使用")

@Composable fun SettingsView(c:DesktopController) {
    CompositionLocalProvider(LocalSettingsSurface provides true){SettingsContent(c)}
}

@Composable private fun SettingsContent(c:DesktopController) {
    var section by c::settingsSection
    Box(Modifier.fillMaxSize(),contentAlignment=Alignment.TopStart) {
        Row(Modifier.fillMaxSize().semantics {testTag="settings-content"}) {
            Column(Modifier.width(212.dp).fillMaxHeight().background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha=.60f)).padding(horizontal=12.dp,vertical=18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
                CompactInput(c.settingsSearch,{c.settingsSearch=it},"查找设置")
                val query=c.settingsSearch.trim()
                val matches=settingsGroups.values.flatten().filter {query.isBlank()||it.contains(query,true)||settingsKeywords[it].orEmpty().contains(query,true)}
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    settingsGroups.forEach {(group,tabs)->
                        if(tabs.any {it in matches}) {
                            SubtleText(group,Modifier.padding(top=12.dp,bottom=5.dp,start=10.dp))
                            tabs.filter {it in matches}.forEach {tab->
                                Row(Modifier.fillMaxWidth().desktopClick(selected=section==tab){section=tab}.padding(horizontal=12.dp,vertical=12.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(9.dp)) {
                                    Glyph(settingsIcon(tab),Modifier.size(18.dp),if(section==tab)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant)
                                    Text(tr(if(tab=="Skills")"技能"else tab),fontSize=14.sp,maxLines=1)
                                }
                            }
                        }
                    }
                    if(matches.isEmpty())SubtleText("没有匹配的设置",maxLines=2)
                }
                SubtleText("Hermes ${BuildConfig.VERSION_NAME}",Modifier.padding(start=10.dp))
            }
            VerticalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.6f))
            key(c.profile,section) {
                val scroll=rememberScrollState()
                Box(Modifier.weight(1f).fillMaxHeight()) {
                    Column(Modifier.fillMaxSize().verticalScroll(scroll).padding(start=32.dp,end=28.dp,top=26.dp,bottom=26.dp),verticalArrangement=Arrangement.spacedBy(14.dp),horizontalAlignment=Alignment.CenterHorizontally) {
                        Column(Modifier.widthIn(max=920.dp).fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(5.dp)) {
                            Heading(if(section=="Skills")"技能"else section)
                            SubtleText(when(section){"外观与账户"->"让工作空间成为你喜欢的样子";"Skills"->"为 Hermes 配备适合你的做事方法";"语音"->"自然地说，也舒适地听";"对话与记忆"->"决定 Hermes 怎样与你合作";else->"按你的习惯，调整工作方式"})
                        }
                        Box(Modifier.widthIn(max=920.dp).fillMaxWidth()) {
                            Column(verticalArrangement=Arrangement.spacedBy(14.dp)) {
                                if(c.settingsLoading)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
                                c.settingsError?.let {error->SyncProblem(c,"部分设置未能读取",error,c.settingsLoading){c.loadSettings()}}
                                when(section) {
                                    "外观与账户"->AppearanceSettings(c)
                                    "模型供应商"->ProviderSettings(c)
                                    "模型"->{if(c.modelCatalogError==null)c.settings?.let {ModelSettings(c,it.models)};ProviderCatalog(c)}
                                    "对话与记忆"->{InputReadingSettings(c);c.settings?.let {ConversationSettings(c,it)}}
                                    "语音"->VoiceSettings(c)
                                    "桌面助手"->DesktopAssistantSettings(c)
                                    "通知"->SettingCard("通知偏好","在工作被打断之前，只提醒值得你留意的事情。") {
                                        Toggle("启用桌面通知",c.notifications){c.notifications=it;c.savePreference("notifications",it.toString())}
                                        Toggle("新回复通知",c.notificationMessages){c.notificationMessages=it;c.savePreference("notificationMessages",it.toString())}
                                        Toggle("审批与澄清通知",c.notificationTasks){c.notificationTasks=it;c.savePreference("notificationTasks",it.toString())}
                                        if(DesktopHost.isWindows)SubtleText("提示音由 Windows 系统通知设置管理。",maxLines=2)
                                        else Toggle("通知声音",c.notificationSound){c.notificationSound=it;c.savePreference("notificationSound",it.toString())}
                                        Toggle(if(DesktopHost.isWindows)"任务栏与托盘未读标记"else"Dock 未读标记",c.notificationBadge){c.notificationBadge=it;c.savePreference("notificationBadge",it.toString())}
                                        SmallButton("发送测试通知",{DesktopNotifications.show("Hermes",tr("这是一条测试通知"),c.notificationSound);c.notice="已请求发送测试通知"})
                                    }
                                    "提示词片段"->SettingCard("常用提示词","保存常用写法，聊天时从输入框插入。") {SnippetsPanel(c)}
                                    "Skills","工具集","MCP"->CapabilitiesSettings(c,section)
                                    "网关"->SettingCard("当前网关","这里连接的是服务器工作空间；本机文件作为附件上传后才会参与对话。") {GatewaySettings(c)}
                                    "帮助"->HelpSettings(c)
                                }
                                if(c.settings==null&&section in listOf("模型","对话与记忆"))SettingCard("读取服务器设置") {Caption("尚未取得配置，可以重新读取。");SmallButton("重新读取",{c.loadSettings()})}
                            }
                        }
                    }
                    VerticalScrollbar(rememberScrollbarAdapter(scroll),Modifier.align(Alignment.CenterEnd).fillMaxHeight().width(6.dp))
                }
            }
        }
    }
}


private fun settingsIcon(section:String)=when(section){"外观与账户"->"palette";"桌面助手"->"expand";"对话与记忆"->"history";"语音"->"mic";"通知"->"notification";"提示词片段"->"bookmark";"模型","模型供应商"->"model";"Skills"->"files";"工具集"->"tool";"MCP"->"connect";"网关"->"workspace";else->"help"}

@Composable private fun DesktopAssistantSettings(c:DesktopController) {
    SettingCard("桌面悬浮球","随手提问、看图、读文档。悬浮球会保持在普通桌面窗口上方。",trailing={StatusPill("本机设置",muted=true)}) {
        Toggle("显示桌面悬浮球",c.floatingAssistantEnabled){c.floatingAssistantEnabled=it;c.savePreference("floatingAssistantEnabled",it.toString())}
        SubtleText("每次打开默认进入日常助理，也可以在小窗上方切换会话。附件先加入草稿，点击发送后才提交。",maxLines=3)
        HorizontalDivider(color=MaterialTheme.colorScheme.outline)
        listOf(Triple("history","单击","打开快捷提问"),Triple("files","拖入文件","添加文档并提问"),Triple("capture","双击","框选截图；Esc 或右键取消"),Triple("mic","按住左键","按住说话，松开后识别并发送"),Triple("move","按住拖动","移动位置，自动记住停靠位置")).forEach {(icon,gesture,detail)->
            Row(Modifier.fillMaxWidth().padding(vertical=7.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Glyph(icon,Modifier.size(20.dp),MaterialTheme.colorScheme.primary)
                Text(gesture,Modifier.width(90.dp),fontSize=14.sp)
                SubtleText(detail,Modifier.weight(1f),maxLines=2)
            }
        }
        SmallButton("试着问一下",{c.floatingAssistantEnabled=true;c.savePreference("floatingAssistantEnabled","true");c.companion.open()})
    }
    SettingCard("后台运行","关闭主窗口后，正在执行的任务继续运行。") {
        Text("点击右上角关闭按钮，Hermes 会收起到右下角系统托盘。单击托盘图标可恢复；右键选择‘退出 Hermes’才完整退出。",fontSize=14.sp)
        SubtleText("如果系统托盘暂时不可用，窗口会最小化到任务栏，确保随时可以重新打开。",maxLines=3)
    }
}

@Composable private fun InputReadingSettings(c:DesktopController) {
    SettingCard("输入与发送","修改后立即生效，也可以点击输入框下方的快捷键提示切换。",trailing={StatusPill("本机设置",muted=true)}) {
        listOf(true,false).forEach {enter->
            Row(Modifier.fillMaxWidth().desktopClick(selected=c.sendOnEnter==enter){c.setSendMode(enter)}.padding(horizontal=10.dp,vertical=10.dp).semantics {testTag=if(enter)"setting-send-enter"else"setting-send-ctrl"},verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                RadioButton(c.sendOnEnter==enter,onClick=null)
                Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Text(if(enter)"Enter 发送"else shortcutKey()+"+Enter 发送",fontWeight=FontWeight.Medium,fontSize=14.sp)
                    SubtleText(if(enter)"Shift+Enter 换行；输入法选字不会发送。"else"Enter 换行；组合键发送。")
                }
            }
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outline)
        SettingLine("任务运行中发送"){Picker(runningSendLabel(c.runningSendMode),listOf("queue","steer","interrupt"),::runningSendLabel){c.runningSendMode=it;c.savePreference("runningSendMode",it)}}
        SubtleText("补充指令需要当前网关支持；带附件的内容可排队发送。",maxLines=2)
    }
    SettingCard("阅读与过程","控制聊天中显示多少执行信息。") {
        SettingLine("过程展示"){Picker(activityModeLabel(c.activityMode),listOf("compact","expanded","answer"),::activityModeLabel){c.activityMode=it;c.savePreference("activityMode",it)}}
        Toggle("显示会话大纲",c.showOutline){c.showOutline=it;c.savePreference("showOutline",it.toString())}
        SubtleText("阅读历史时保留位置，点击“回到底部”恢复跟随新回复。内部运行提示以简短状态记录显示。",maxLines=3)
    }
}

@Composable private fun ProviderCatalog(c:DesktopController) {
    var query by remember {mutableStateOf("")}
    LaunchedEffect(c.profile){c.loadModelCatalog()}
    SettingCard("提供商与模型目录","按名称查找，展开查看模型；会话模型在输入框切换。") {
        if(c.modelCatalogLoading)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
        if(c.modelCatalogError!=null)ModelCatalogIssue(c)
        if(c.modelCatalogError==null)CompactInput(query,{query=it},"搜索提供商或模型")
        val visible=if(c.modelCatalogError!=null)emptyList()else c.modelCatalog.providers.filter {p->query.isBlank()||p.name.contains(query,true)||p.models.any {it.contains(query,true)}}
        if(visible.isEmpty()&&c.modelCatalogError==null&&!c.modelCatalogLoading)SubtleText(if(query.isBlank())"这个工作空间还没有可选模型，请先配置提供商。"else"没有匹配的模型，试试其他关键词",maxLines=2)
        visible.forEach {p->
            Disclosure(p.name,p.models.size.toString()+" 个模型",initiallyOpen=query.isNotBlank()) {
                p.models.filter {query.isBlank()||p.name.contains(query,true)||it.contains(query,true)}.forEach {model->
                    Row(Modifier.fillMaxWidth().padding(vertical=5.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                        Glyph("model",Modifier.size(16.dp),MaterialTheme.colorScheme.primary);Text(model,Modifier.weight(1f),fontSize=13.sp)
                        if(p.slug==c.modelCatalog.currentProvider&&model==c.modelCatalog.currentModel)StatusPill("默认")
                    }
                }
            }
            HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.5f))
        }
    }
}

@Composable private fun HelpSettings(c:DesktopController) {
    DesktopUpdateSettings(c)
    SettingCard("Hermes ${DesktopHost.os.label} ${BuildConfig.VERSION_NAME}","对话、任务和文件，在一个工作空间里继续。") {
        listOf("新对话" to "Ctrl N","快速查找" to "Ctrl K","命令面板" to "Ctrl P","展开或收起导航" to "Ctrl B","专注模式" to "Ctrl Shift F","保存文档" to "Ctrl S","发送消息" to if(c.sendOnEnter)"Enter"else"Ctrl+Enter","打开设置" to "Ctrl ,").forEach {(label,keys)->SettingLine(label){Text(keys.replace("Ctrl",shortcutKey()),fontFamily=FontFamily.Monospace,fontSize=13.sp)}}
    }
    SettingCard("几个顺手的用法") {
        Text(tr("""• 左侧切换工作空间，最近会话支持搜索与时间分组。
• 在输入框切换模型，点击快捷键提示可改变发送方式。
• 顶部大纲可定位问题，文件按钮可打开产物与目录。
• 拖动文件栏左边缘调整宽度。
• 选中文字后右键，可以引用到对话。
• 打开文档后选择“边看边聊”，在旁边提出修改。
• 文档和设置的未保存修改会保留为本机草稿。
• 将文件拖入窗口即可添加附件；发送前可以移除。"""))
    }
    SettingCard("连接与反馈") {
        Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){SmallButton("连接诊断",{c.settingsSection="网关";c.diagnoseConnection()});SmallButton("复制版本信息",{DesktopFiles.copy("Hermes ${BuildConfig.VERSION_NAME} · ${DesktopHost.os.label}");c.notice="版本信息已复制"})}
    }
}

@Composable private fun AppearanceSettings(c:DesktopController) {
    val profileDraft=settingDraft(c,"appearance",mapOf("name" to c.nickname,"assistant" to c.hermesName,"bio" to c.biography),SettingCodec::text,SettingCodec::text)
    val name=profileDraft.value["name"].orEmpty();val assistant=profileDraft.value["assistant"].orEmpty();val bio=profileDraft.value["bio"].orEmpty()
    var avatar by remember {mutableStateOf<Boolean?>(null)}
    SectionCard(Modifier.fillMaxWidth()) {
        SectionTitle("界面主题","选择适合你的工作氛围")
        Row(Modifier.fillMaxWidth().padding(start=18.dp,end=18.dp,bottom=18.dp),horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            listOf("轻盈办公","纸间留白","流光玻璃").forEach {skin->SkinOption(skin,skinDisplayName(c.skin)==skin,Modifier.weight(1f)){c.skin=skin;c.savePreference("workspaceSkinV2",skin)}}
        }
        HorizontalDivider(color=MaterialTheme.colorScheme.outline.copy(alpha=.5f))
        Column(Modifier.padding(horizontal=20.dp,vertical=8.dp)) {
            SettingLine("显示模式"){Picker(c.appearance,listOf("浅色","深色","跟随系统"),{tr(it)}){c.appearance=it;c.savePreference("appearance",it)}}
            SettingLine("界面语言"){Picker(when(c.language){"en"->"English";"zh"->"简体中文";else->tr("跟随系统")},listOf("system","zh","en"),{when(it){"en"->"English";"zh"->"简体中文";else->tr("跟随系统")}}){c.changeLanguage(it)}}
            SettingLine("界面密度"){Picker(if(c.compact)"紧凑"else"标准",listOf(true,false),{if(it)"紧凑"else"标准"}){c.compact=it;c.savePreference("compact",it.toString())}}
            SettingLine("文字大小"){Picker("${(c.textScale*100).toInt()}%",listOf(.9f,1f,1.1f,1.2f,1.3f),{"${(it*100).toInt()}%"}){c.textScale=it;c.savePreference("textScale",it.toString())}}
            Toggle("减少动态效果",c.reduceMotion){c.reduceMotion=it;c.savePreference("reduceMotion",it.toString())}
        }
    }
    SectionCard(Modifier.fillMaxWidth()) {
        SectionTitle("个人资料","你和 Hermes 的称呼与头像")
        Column(Modifier.padding(start=20.dp,end=20.dp,bottom=20.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement=Arrangement.spacedBy(16.dp),verticalAlignment=Alignment.CenterVertically) {
                Avatar(c,true,Modifier.size(36.dp).desktopClick {avatar=true});DeskTextButton(onClick={avatar=true}){Text(tr("我的头像"))}
                Spacer(Modifier.width(8.dp))
                Avatar(c,false,Modifier.size(36.dp).desktopClick {avatar=false});DeskTextButton(onClick={avatar=false}){Text(tr("Hermes 头像"))}
            }
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)){Field("你的称呼",name,{profileDraft.value=profileDraft.value+("name" to it)})}
                Box(Modifier.weight(1f)){Field("助理名称",assistant,{profileDraft.value=profileDraft.value+("assistant" to it)})}
            }
            Field("个人简介",bio,{profileDraft.value=profileDraft.value+("bio" to it)})
            SmallButton("保存资料",{c.nickname=name.trim().ifBlank {"Jerome"};c.hermesName=assistant.trim().ifBlank {"Hermes"};c.biography=bio;c.savePreference("nickname",c.nickname);c.savePreference("hermesName",c.hermesName);c.savePreference("biography",bio);profileDraft.commit(profileDraft.value,mapOf("name" to c.nickname,"assistant" to c.hermesName,"bio" to c.biography));c.notice="个人资料已保存"},true,enabled=profileDraft.dirty)
        }
    }
    Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        DeskTextButton(onClick={c.openProfileFile(HermesProfileFile.MEMORY)}){Text(tr("我的记忆 · MEMORY.md"))}
        DeskTextButton(onClick={c.openProfileFile(HermesProfileFile.SOUL)}){Text(tr("我的心智 · SOUL.md"))}
    }
    avatar?.let {user->AvatarEditor(c,user){avatar=null}}
}

@Composable private fun SkinOption(name:String,selected:Boolean,modifier:Modifier,onClick:()->Unit) {
    val colors=MaterialTheme.colorScheme;val shape=RoundedCornerShape(10.dp)
    Column(modifier.clip(shape).background(if(selected)colors.primary.copy(alpha=.045f)else colors.background.copy(alpha=.35f))
        .border(1.dp,if(selected)colors.primary.copy(alpha=.65f)else colors.outline,shape)
        .semantics {testTag="skin-$name";this.selected=selected}
        .desktopClick(shape=shape,fillHover=false,onClick=onClick).padding(10.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
        SkinThumbnail(name,Modifier.fillMaxWidth().aspectRatio(1.6f))
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
            Text(tr(name),Modifier.weight(1f),fontSize=13.sp,lineHeight=20.sp,fontWeight=FontWeight.Medium,maxLines=1,overflow=TextOverflow.Ellipsis)
            if(selected)Glyph("check",Modifier.size(16.dp),colors.primary)
        }
        Text(tr(when(name){"纸间留白"->"纸张肌理 · 宽松阅读";"流光玻璃"->"流光背景 · 悬浮面板";else->"通透导航 · 清晰分栏"}),fontSize=11.sp,lineHeight=18.sp,color=colors.onSurfaceVariant)
    }
}

@Composable private fun ModelSettings(c:DesktopController,initial:ServerModelSettings) {
    val draft=settingDraft(c,"models",initial,SettingCodec::model,SettingCodec::model)
    var value by draft
    val catalog=c.modelCatalog.providers.flatMap {p->p.models.map {p.slug to it}}
    val validation=when {
        value.provider.isBlank()||value.model.isBlank()->"请选择服务商和模型。"
        value.fallbackModels.any {it.provider.isBlank()||it.model.isBlank()}->"请选择备用模型，或移除空白项。"
        else->null
    }
    SettingCard("默认对话模型","用于新对话；已有会话可以在底部输入框单独切换。") {
        if(catalog.isNotEmpty())Picker(value.model.ifBlank {"选择已配置模型"},catalog,{"${it.first} / ${it.second}"}){value=value.copy(provider=it.first,model=it.second)}
        Disclosure("手动填写模型",initiallyOpen=catalog.isEmpty()) {
        Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)){Field("服务商",value.provider,{value=value.copy(provider=it)})}
            Box(Modifier.weight(1f)){Field("模型标识",value.model,{value=value.copy(model=it)})}
        }
        }
        SettingLine("推理强度"){Picker(value.reasoningEffort.ifBlank {"默认"},listOf("","none","minimal","low","medium","high","xhigh","max"),{optionLabel(it.ifBlank {"默认"})}){value=value.copy(reasoningEffort=it)}}
        SettingsSaveBar(c,draft,validation=validation,save={api,v->api.saveModelSettings(v)},extract={it.models})
    }
    SettingCard("进阶配置") {
        Disclosure("上下文窗口","默认采用模型配置；需要限制长度时再调整。") {IntegerField("上下文窗口，0 表示默认",value.contextLength,0..2_000_000,draft){value=value.copy(contextLength=it)}}
        Disclosure("辅助模型","图像理解、网页提取、内容压缩等任务使用的模型。") {
            (value.auxiliary.keys+listOf("vision","web_extract","compression","skills_hub","approval","mcp","title_generation","curator")).distinct().forEach {key->
                val choice=value.auxiliary[key]?:ModelChoice()
                Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
                    Hint(key){Text(tr(optionLabel(key)),Modifier.width(128.dp),fontSize=13.sp)}
                    Picker(choice.model.ifBlank {"自动"},listOf("auto" to "")+catalog,{if(it.second.isBlank())"自动"else "${it.first} / ${it.second}"}){selected->value=value.copy(auxiliary=value.auxiliary+(key to ModelChoice(selected.first,selected.second)))}
                }
            }
        }
        Disclosure("备用模型","主模型不可用时，按列表顺序尝试。") {
            value.fallbackModels.forEachIndexed {index,choice->
                Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Text("${index+1}",fontSize=12.sp)
                    Box(Modifier.weight(1f)){Picker(choice.model.ifBlank {"选择模型"},catalog,{"${it.first} / ${it.second}"}){selected->value=value.copy(fallbackModels=value.fallbackModels.mapIndexed {i,m->if(i==index)FallbackModel(selected.first,selected.second)else m})}}
                    DeskTextButton(onClick={val list=value.fallbackModels.toMutableList();if(index>0){java.util.Collections.swap(list,index,index-1);value=value.copy(fallbackModels=list)}},enabled=index>0){Glyph("arrow-up",Modifier.size(16.dp))}
                    DeskIconButton(onClick={value=value.copy(fallbackModels=value.fallbackModels.filterIndexed {i,_->i!=index})}){Glyph("close",Modifier.size(16.dp))}
                }
            }
            SmallButton("添加备用模型",{value=value.copy(fallbackModels=value.fallbackModels+FallbackModel())})
        }
        Disclosure("专家会审","多个模型分别作答，再汇总观点。") {
            Field("参与模型，每行一个",value.moaReferenceModels.joinToString("\n"),{value=value.copy(moaReferenceModels=it.lines().map(String::trim).filter(String::isNotBlank))},3)
            Field("汇总模型",value.moaAggregatorModel,{value=value.copy(moaAggregatorModel=it)})
        }
        if(draft.dirty)SettingsSaveBar(c,draft,validation=validation,save={api,v->api.saveModelSettings(v)},extract={it.models})
        SmallButton("配置模型供应商",{c.settingsSection="模型供应商"})
    }
}

@Composable private fun ConversationSettings(c:DesktopController,all:ServerSettings) {
    val styleDraft=settingDraft(c,"conversation",all.conversation,SettingCodec::conversation,SettingCodec::conversation)
    val approvalDraft=settingDraft(c,"approval",all.approvals,SettingCodec::approval,SettingCodec::approval)
    val memoryDraft=settingDraft(c,"memory",all.memory,SettingCodec::memory,SettingCodec::memory)
    var style by styleDraft;var approval by approvalDraft;var memory by memoryDraft
    SettingCard("对话方式") {
        ReplyStyleSelector(style.personality,all.rawConfig){style=style.copy(personality=it)}
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
            Box(Modifier.weight(1f)){Field("时区",style.timezone,{style=style.copy(timezone=it)})}
            Picker("常用时区",listOf("Asia/Shanghai","Asia/Hong_Kong","Asia/Tokyo","America/Los_Angeles","America/New_York","Europe/London","UTC"),{it}){style=style.copy(timezone=it)}
        }
        Toggle("显示可展开的推理过程",style.showReasoning){style=style.copy(showReasoning=it)}
        SettingsSaveBar(c,styleDraft,validation=if(style.timezone.isNotBlank()&&runCatching {java.time.ZoneId.of(style.timezone)}.isFailure)"请输入有效时区，例如 Asia/Shanghai。"else null,save={api,v->api.saveConversationStyle(v)},extract={it.conversation})
    }
    SettingCard("记忆与档案","让 Hermes 记住对你有用的信息。") {
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            SmallButton("查看与编辑记忆",{c.openProfileFile(HermesProfileFile.MEMORY)})
            SmallButton("查看与编辑助理设定",{c.openProfileFile(HermesProfileFile.SOUL)})
        }
        Toggle("启用长期记忆",memory.memoryEnabled){memory=memory.copy(memoryEnabled=it)}
        Toggle("启用用户档案",memory.userProfileEnabled){memory=memory.copy(userProfileEnabled=it)}
        Disclosure("容量与上下文压缩","控制记忆长度和历史消息的保留方式。") {
            Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                Box(Modifier.weight(1f)){IntegerField("记忆字符上限",memory.memoryCharLimit,100..100_000,memoryDraft){memory=memory.copy(memoryCharLimit=it)}}
                Box(Modifier.weight(1f)){IntegerField("用户档案字符上限",memory.userCharLimit,100..100_000,memoryDraft){memory=memory.copy(userCharLimit=it)}}
            }
            Toggle("自动压缩上下文",memory.compressionEnabled){memory=memory.copy(compressionEnabled=it)}
            Percentage("压缩触发比例",memory.compressionThreshold,.10f.. .95f){memory=memory.copy(compressionThreshold=it)}
            Percentage("压缩后目标比例",memory.compressionTargetRatio,.05f.. .80f){memory=memory.copy(compressionTargetRatio=it)}
            IntegerField("保护最近消息数",memory.protectLastMessages,0..200,memoryDraft){memory=memory.copy(protectLastMessages=it)}
        }
        SettingsSaveBar(c,memoryDraft,validation=if(memory.compressionEnabled&&memory.compressionTargetRatio>=memory.compressionThreshold)"压缩后的目标比例应小于触发比例。"else null,save={api,v->api.saveMemorySettings(v)},extract={it.memory})
    }
    SettingCard("执行审批","决定哪些操作需要先由你确认。") {
        SettingLine("审批模式"){Picker(approval.mode,listOf("smart","always","never"),::optionLabel){approval=approval.copy(mode=it)}}
        SubtleText(when(approval.mode){"always"->"执行需要审批的操作时，始终先询问。";"never"->"按服务器规则直接执行，请在了解任务内容后使用。";else->"由 Hermes 根据操作内容决定是否需要确认。"},maxLines=3)
        IntegerField("等待确认的时间（秒）",approval.timeoutSeconds,10..3600,approvalDraft){approval=approval.copy(timeoutSeconds=it)}
        SettingsSaveBar(c,approvalDraft,save={api,v->api.saveApprovalSettings(v)},extract={it.approvals})
    }
}

@Composable private fun VoiceSettings(c:DesktopController) {
    val pref=c.voicePreferences
    fun change(v:VoicePreferences)=c.saveVoicePreferences(v)
    SettingCard("语音输入","本机偏好立即生效；普通录音和连续语音可以分别使用。") {
        Toggle("启用语音输入",pref.enabled){change(pref.copy(enabled=it))}
        SettingLine("识别与朗读引擎"){Picker(pref.engine,listOf("automatic","server","system"),{when(it){"server"->"服务器";"system"->"${DesktopHost.os.label} 系统";else->"自动选择"}}){change(pref.copy(engine=it))}}
        SubtleText(when(pref.engine){"server"->"使用服务器配置的识别与朗读能力。";"system"->"使用本机已安装的语音语言和音色。";else->"根据本机与服务器可用能力自动选择，测试结果会显示在下方。"},maxLines=3)
        SettingLine("识别语言"){Picker(pref.language,listOf("zh-CN","zh-TW","en-US","ja-JP","ko-KR"),::optionLabel){change(pref.copy(language=it))}}
        SettingLine("中文转写"){Picker(pref.transcriptScript,listOf("simplified","traditional","original"),::optionLabel){change(pref.copy(transcriptScript=it))}}
        Toggle("普通录音转写后自动发送",pref.autoSend){change(pref.copy(autoSend=it))}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            SmallButton(if(c.recording)"完成识别测试"else"测试麦克风与识别",{if(c.recording)c.voice.stopCapture()else c.voice.startCapture(false,true)},enabled=!c.voiceBusy)
            if(c.voice.message.isNotBlank())SubtleText(c.voice.message,Modifier.weight(1f),maxLines=3)
        }
    }
    SettingCard("连续对话与朗读") {
        Toggle("语音对话自动朗读回复",pref.autoRead){change(pref.copy(autoRead=it))}
        Toggle("朗读结束后继续聆听",pref.continuous){change(pref.copy(continuous=it))}
        Toggle("语音快速回复",pref.fastReply){change(pref.copy(fastReply=it))}
        SettingLine("环境噪声适应"){Picker(pref.noiseSensitivity,listOf("quiet","balanced","noisy"),::optionLabel){change(pref.copy(noiseSensitivity=it))}}
        SettingLine("朗读速度"){Picker("%.2f×".format(pref.speechRate),listOf(.6f,.8f,1f,1.2f,1.5f,1.8f),{"%.2f×".format(it)}){change(pref.copy(speechRate=it))}}
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {SmallButton("测试朗读",{c.speak("你好，我是 Hermes。这是一段朗读测试。")});SmallButton("停止",{c.voice.pause()})}
    }
    SettingCard("服务器配置") {
        Disclosure("识别模型与朗读音色","仅在需要调整服务器语音能力时修改。") {
            c.settings?.let {all->
                val draft=settingDraft(c,"voice",all.voice,SettingCodec::voice,SettingCodec::voice);var value by draft
                Toggle("启用服务器识别",value.stt.enabled){value=value.copy(stt=value.stt.copy(enabled=it))}
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)){Field("识别服务商",value.stt.provider,{value=value.copy(stt=value.stt.copy(provider=it))})}
                    Box(Modifier.weight(1f)){Field("识别模型",value.stt.model,{value=value.copy(stt=value.stt.copy(model=it))})}
                }
                Field("服务器识别语言",value.stt.language,{value=value.copy(stt=value.stt.copy(language=it))})
                Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
                    Box(Modifier.weight(1f)){Field("合成服务商",value.tts.provider,{value=value.copy(tts=value.tts.copy(provider=it))})}
                    Box(Modifier.weight(1f)){Field("合成模型",value.tts.model,{value=value.copy(tts=value.tts.copy(model=it))})}
                }
                Field("音色",value.tts.voice,{value=value.copy(tts=value.tts.copy(voice=it))})
                SettingsSaveBar(c,draft,save={api,v->api.saveVoiceSettings(v)},extract={it.voice})
            }?:SmallButton("读取服务器语音配置",{c.loadSettings()})
        }
    }
    SettingCard("录音记录","识别失败的录音可以重试，已识别文字可以再次填入草稿。") {
        val notes=c.voiceNotes.filter {it.profile==c.profile}.reversed()
        if(notes.isEmpty())SubtleText("暂无录音记录。")
        notes.forEach {note->
            Text(note.session?.title?:"识别测试",fontWeight=FontWeight.Medium)
            SubtleText(note.transcript.ifBlank {"尚未识别"},maxLines=3)
            Row {DeskTextButton(onClick={c.voice.retry(note)},enabled=!c.recording&&!c.voiceBusy){Text(tr("重新识别"))};DeskTextButton(onClick={c.voice.reuse(note)},enabled=note.session!=null&&note.transcript.isNotBlank()){Text(tr("填入草稿"))};DeskTextButton(onClick={c.voice.remove(note)}){Text(tr("删除"))}}
            HorizontalDivider(color=MaterialTheme.colorScheme.outline)
        }
    }
}

@Composable private fun GatewaySettings(c:DesktopController) {
    var logout by remember {mutableStateOf(false)};var update by remember {mutableStateOf(false)}
    Text(c.baseUrl.ifBlank {tr("演示连接")});Caption("${c.username.ifBlank {c.nickname}} · ${c.profile}")
    Caption("Agent ${c.gateway.agentVersion.ifBlank {"—"}} · Gateway ${c.gateway.gatewayVersion.ifBlank {"—"}}")
    Row(horizontalArrangement=Arrangement.spacedBy(10.dp)) {SmallButton("连接诊断",{c.diagnoseConnection()});SmallButton("重新连接网关",{c.request(block={it.reconnectGateway();it.gatewayInfo()}){c.gateway=it;c.retryRecovery();c.notice="已重新连接。"}})}
    if(c.diagnosticBusy)LinearProgressIndicator(Modifier.fillMaxWidth().height(2.dp))
    c.diagnosticLines.forEach {(name,ok)->
        Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            Glyph(if(ok)"check"else"alert",Modifier.size(16.dp),if(ok)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error)
            Text(tr(name),Modifier.weight(1f),fontSize=13.sp);StatusPill(if(ok)"通过"else"未通过",error=!ok)
            c.diagnosticDetails[name]?.let {detail->DeskTextButton(onClick={c.showDetails(name,detail)}){Text(tr("详情"))}}
        }
    }
    if(c.diagnosticLines.isNotEmpty())SmallButton("复制诊断结果",{DesktopFiles.copy("Hermes "+BuildConfig.VERSION_NAME+"\n"+c.diagnosticLines.joinToString("\n"){(name,ok)->name+"："+(if(ok)"通过"else"未通过")+c.diagnosticDetails[name]?.let {" · "+it}.orEmpty()});c.notice="诊断结果已复制"})
    Text(if(c.sessionsLoading)"会话同步中 · 已读取 "+c.sessions.size+" 段"else if(c.sessionsLoadError!=null)"会话同步未完成 · 已保留 "+c.sessions.size+" 段"else"当前工作空间 · "+c.sessions.size+" 段会话",fontSize=13.sp)
    c.sessionsLoadError?.let {SyncProblem(c,"会话读取失败",it,c.sessionsLoading){c.refresh()}}
    SmallButton("检查 Hermes Agent 更新",{c.request(block={it.checkAgentUpdate(true)}){c.updateInfo=it}},enabled=!c.updating)
    c.updateInfo?.let {info->Text(info.message);Caption(info.currentVersion);info.commits.take(12).forEach {Caption(it.summary)};if(info.updateAvailable&&info.canApply)SmallButton("更新服务器 Agent",{update=true},true,enabled=!c.updating)}
    c.updateProgress?.let {progress->if(progress.running)LinearProgressIndicator(Modifier.fillMaxWidth());Text(progress.lines.takeLast(16_000),fontFamily=FontFamily.Monospace,fontSize=12.sp);SmallButton("刷新更新状态",{c.request(block={it.agentUpdateStatus()}){c.updateProgress=it}})}
    SmallButton("退出登录",{logout=true})
    if(update)ConfirmDialog("更新服务器 Agent？","更新会重新启动服务器网关。请确认当前没有需要保留的运行任务。",{update=false}){update=false;c.startAgentUpdate()}
    if(logout)ConfirmDialog("退出当前网关？","本机草稿会保留，实时连接将关闭。",{logout=false}){logout=false;c.disconnect()}
}

@Composable private fun SettingLine(label:String,control:@Composable ()->Unit) {Row(Modifier.widthIn(max=720.dp).fillMaxWidth().heightIn(min=44.dp).padding(vertical=3.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)){Text(tr(label),Modifier.weight(1f));control()}}
@Composable internal fun Toggle(label:String,value:Boolean,change:(Boolean)->Unit) {
    Row(Modifier.widthIn(max=720.dp).fillMaxWidth().clip(RoundedCornerShape(8.dp)).toggleable(value=value,role=Role.Switch,onValueChange=change).heightIn(min=44.dp).padding(horizontal=4.dp,vertical=2.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(20.dp)) {
        Text(tr(label),Modifier.weight(1f));DesktopToggle(value)
    }
}
@Composable internal fun Field(label:String,value:String,change:(String)->Unit,lines:Int=1)=FormInput(label,value,change,lines)
@Composable private fun IntegerField(label:String,value:Int,range:IntRange,draft:SettingDraft<*>,change:(Int)->Unit) {
    val text=draft.rawInputs[label]?:value.toString()
    val valid=text.toIntOrNull() in range
    FormInput(label,text,{next->
        draft.rawInputs[label]=next
        val parsed=next.toIntOrNull()
        if(parsed!=null&&parsed in range){draft.fieldErrors.remove(label);change(parsed)}
        else draft.fieldErrors[label]="$label：请输入 ${range.first}–${range.last} 之间的整数。"
    },error=if(valid)null else "请输入 ${range.first}–${range.last} 之间的整数。")
}
@Composable private fun Percentage(label:String,value:Double,range:ClosedFloatingPointRange<Float>,change:(Double)->Unit) {Caption("${tr(label)} · ${(value*100).toInt()}%");Slider(value.toFloat().coerceIn(range.start,range.endInclusive),{change(it.toDouble())},valueRange=range)}
