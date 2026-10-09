@file:OptIn(androidx.compose.ui.ExperimentalComposeUiApi::class,androidx.compose.ui.InternalComposeUiApi::class)
package com.qingyu.hermescompanion.desktop

import androidx.compose.ui.*
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.layout.*
import com.qingyu.hermescompanion.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.swing.Swing
import org.junit.Test
import org.junit.Assert.*

class DecisionLayout201Test {
    @Test fun longCommandCannotPushActionsOutsideSmallWindow()=runBlocking<Unit>(Dispatchers.Swing){
        val c=DesktopController(true);c.reduceMotion=true;c.textScale=1.3f;setDesktopLanguage("zh")
        val pending=c.decisions.values.first().let {it.copy(request=it.request.copy(
            title=(1..120).joinToString("\n"){"long command line with several arguments and readable details"},
            detail="请核对命令后再决定。",choices=listOf(AgentRequestChoice("拒绝","deny"),AgentRequestChoice("本会话允许","session"),AgentRequestChoice("允许一次","once"))
        ))}
        c.decisions.clear();c.decisions["layout"]=pending
        var dismissed=false
        val scene=ImageComposeScene(720,480,coroutineContext=Dispatchers.Swing){HermesTheme(c){Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){DecisionDialog(c,pending,true){dismissed=true}}}}
        fun walk(n:SemanticsNode):List<SemanticsNode> = listOf(n)+n.children.flatMap(::walk)
        fun nodes()=scene.semanticsOwners.flatMap {walk(it.unmergedRootSemanticsNode)}
        try{
            repeat(8){scene.render(System.nanoTime()).close();delay(30)}
            val actions=nodes().first {it.config.getOrNull(SemanticsProperties.TestTag)=="decision-actions"}
            assertTrue(actions.boundsInRoot.bottom<=480f);assertTrue(actions.boundsInRoot.top>=0f)
            for(label in listOf("拒绝","本会话允许","允许一次","稍后处理")){
                val node=nodes().first {it.config.getOrNull(SemanticsProperties.Text)?.any {t->t.text==label}==true}
                assertTrue("Action hidden: $label",node.boundsInRoot.height>0&&node.boundsInRoot.bottom<=480f)
            }
            val scroll=nodes().mapNotNull {it.config.getOrNull(SemanticsProperties.VerticalScrollAxisRange)}.first()
            assertTrue("Long command must scroll",scroll.maxValue()>500f)
            val later=nodes().first {it.config.getOrNull(SemanticsProperties.Text)?.any {t->t.text=="稍后处理"}==true}.boundsInRoot.center
            scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Press,later,button=androidx.compose.ui.input.pointer.PointerButton.Primary)
            scene.sendPointerEvent(androidx.compose.ui.input.pointer.PointerEventType.Release,later,button=androidx.compose.ui.input.pointer.PointerButton.Primary)
            assertTrue(dismissed);assertEquals(pending.request,c.decisions["layout"]?.request)
        }finally{scene.close();c.close()}
    }
}
