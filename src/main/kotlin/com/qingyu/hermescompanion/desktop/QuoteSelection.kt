@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
@file:Suppress("DEPRECATION")
package com.qingyu.hermescompanion.desktop

import androidx.compose.foundation.*
import androidx.compose.foundation.text.TextContextMenu
import androidx.compose.foundation.text.LocalTextContextMenu
import androidx.compose.runtime.*

/** Uses the actual selection; it never reads or replaces the user's clipboard to obtain it. */
@Composable fun QuoteSelection(label:String,onQuote:(String)->Unit,content:@Composable ()->Unit) {
    val action by rememberUpdatedState(onQuote)
    val menu=remember(label) {object:TextContextMenu {
        @Composable override fun Area(textManager:TextContextMenu.TextManager,state:ContextMenuState,content:@Composable ()->Unit) {
            ContextMenuArea(items={buildList {
                textManager.cut?.let {add(ContextMenuItem(tr("剪切"),it))}
                textManager.copy?.let {add(ContextMenuItem(tr("复制"),it))}
                textManager.paste?.let {add(ContextMenuItem(tr("粘贴"),it))}
                textManager.selectAll?.let {add(ContextMenuItem(tr("全选"),it))}
                if(textManager.selectedText.text.isNotBlank())add(ContextMenuItem(tr(label)){action(textManager.selectedText.text)})
            }},state=state,content=content)
        }
    }}
    CompositionLocalProvider(LocalTextContextMenu provides menu,content=content)
}
