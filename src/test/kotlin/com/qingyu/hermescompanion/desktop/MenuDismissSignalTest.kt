package com.qingyu.hermescompanion.desktop

import org.junit.Assert.*
import org.junit.Test

class MenuDismissSignalTest {
    @Test fun mouseThreadQueuesOneDismissWithoutExecutingUiInline() {
        val queue=mutableListOf<()->Unit>();var dismisses=0
        val signal=MenuDismissSignal({queue+=it}){dismisses++}
        repeat(20){signal.outsidePress()}
        assertEquals(0,dismisses);assertEquals(1,queue.size)
        queue.single()();assertEquals(1,dismisses)
    }
    @Test fun closeCancelsAnAlreadyQueuedPress() {
        val queue=mutableListOf<()->Unit>();var dismissed=false
        val signal=MenuDismissSignal({queue+=it}){dismissed=true}
        signal.outsidePress();signal.close();queue.single()()
        assertFalse(dismissed)
    }
    @Test fun delayedPressFromOldMenuCannotCloseReopenedMenu() {
        val queue=mutableListOf<()->Unit>();var oldDismisses=0;var newDismisses=0
        val old=MenuDismissSignal({queue+=it}){oldDismisses++}
        old.outsidePress();old.close()
        val current=MenuDismissSignal({queue+=it}){newDismisses++}
        queue.removeAt(0)();assertEquals(0,oldDismisses);assertEquals(0,newDismisses)
        current.outsidePress();queue.removeAt(0)();assertEquals(1,newDismisses)
    }
    @Test fun closedMonitorIgnoresFurtherPressesAndIsSafeToCloseTwice() {
        val queue=mutableListOf<()->Unit>()
        val signal=MenuDismissSignal({queue+=it}){fail("Closed menu was called")}
        signal.close();signal.close();signal.outsidePress();assertTrue(queue.isEmpty())
    }
}
