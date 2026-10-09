package com.qingyu.hermescompanion.desktop

import com.sun.jna.*
import org.junit.Assert.*
import org.junit.Test

/** Exercises the real JNA callback trampoline without pretending to run Apple's framework on Linux. */
class CocoaBlockAbiTest {
    @Test fun authorizationBlockRoundTripsItsNativeArgumentsAndKeepsItsDescriptor(){
        var seen=false
        val error=Memory(8)
        val callback=object:NotificationAuthorizationCallback {
            override fun invoke(block:Pointer,granted:Byte,value:Pointer?){seen=granted.toInt()==1&&value==error}
        }
        val block=CocoaGlobalBlock(Pointer(1),callback,"v@?B@")
        assertEquals(32L,block.descriptor.getLong(8))
        assertEquals((1 shl 28) or (1 shl 30),block.pointer.getInt(8))
        assertEquals("v@?B@",block.descriptor.getPointer(16).getString(0))
        completeCocoaBlock(block.pointer,1.toByte(),error)
        assertTrue(seen)
    }
    @Test fun systemCompletionBlockIsInvokedWithPresentationOptions(){
        var received=-1L
        val callback=object:PresentationCallback {override fun invoke(block:Pointer,options:Long){received=options}}
        val block=CocoaGlobalBlock(Pointer(1),callback,"v@?Q")
        completeCocoaBlock(block.pointer,26L)
        assertEquals(26L,received)
    }
    interface PresentationCallback:Callback {fun invoke(block:Pointer,options:Long)}
}
