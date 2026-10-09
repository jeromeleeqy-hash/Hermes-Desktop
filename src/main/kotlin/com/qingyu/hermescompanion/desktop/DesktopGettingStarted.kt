package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable internal fun GettingStartedButton(){
    var open by remember {mutableStateOf(false)}
    DeskTextButton(onClick={open=true}){Text("连接与使用指南")}
    if(open)HermesDialog(onDismissRequest={open=false},title={Text("连接你自己的 Hermes")},text={Column(Modifier.widthIn(max=600.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)){
        Text("1. 准备网关",style=MaterialTheme.typography.titleMedium)
        Text("桌面版连接你自己部署的 Hermes App 网关。打开安卓端的连接设置，使用相同的服务器地址和账号；默认端口为 9119。")
        Text("2. 填写可访问的地址",style=MaterialTheme.typography.titleMedium)
        Text("本机部署可以填写 http://127.0.0.1:9119。远程部署使用服务器域名或 IP；127.0.0.1 和 localhost 只代表当前这台电脑。地址有路径前缀时也要保留。HTTPS 反向代理需允许 WebSocket 连接。")
        Text("3. 连接后继续工作",style=MaterialTheme.typography.titleMedium)
        Text("选择与手机相同的档案（Profile）。首页读取该档案的工作区；首次使用可点“更新进展”，再在“首页设置”配置早晚整理。两端共用服务器记录，整理请求的进度在“任务 → 首页任务”查看。")
        Text("4. 语音与离线使用",style=MaterialTheme.typography.titleMedium)
        Text("在“设置 → 语音”分别选择识别和朗读引擎。本地模型下载一次后可在本机转写、朗读；与 Hermes 对话仍需连接网关。录音失败可重试，首页缓存与草稿会保留。")
        Text("遇到连接问题",style=MaterialTheme.typography.titleMedium)
        Text("先用这台电脑的浏览器检查网关地址是否可访问，再核对端口、反向代理和账号。断线期间客户端会核对已发送任务的状态；结果不明时请先查看任务记录。设置里的“连接诊断”可查看登录和实时连接状态。")
    }},confirmButton={SmallButton("知道了",{open=false},true)})
}
