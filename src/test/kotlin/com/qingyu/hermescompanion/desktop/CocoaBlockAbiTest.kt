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
    @Test fun authorizationErrorIsCopiedBeforeTheNativeCallbackReturns(){
        val queue=mutableListOf<()->Unit>()
        var granted:Byte?=null
        var failure:Throwable?=null
        val error=Memory(64).apply {setString(0,"Notification authorization unavailable")}
        val callback=snapshotNotificationAuthorizationCallback(
            describeError={it.getString(0)},
            enqueue={queue+=it},
        ) {value,exception->granted=value;failure=exception}
        val block=CocoaGlobalBlock(Pointer(1),callback,"v@?B@")
        try {
            completeCocoaBlock(block.pointer,0.toByte(),error)
            assertNull(granted)
            assertEquals(1,queue.size)
            // The borrowed object is gone before the main queue gets to run.
            error.clear();error.close()
            queue.removeAt(0).invoke()
            assertEquals(0.toByte(),granted)
            assertEquals("Notification authorization unavailable",failure?.message)
        }finally {error.close()}
    }
    @Test fun authorizationWithoutAnErrorPreservesBothGrantAndDenial(){
        for(value in listOf(0.toByte(),1.toByte())) {
            val queue=mutableListOf<()->Unit>()
            var received:Byte?=null
            val callback=snapshotNotificationAuthorizationCallback(
                describeError={throw AssertionError("No error object was supplied")},
                enqueue={queue+=it},
            ) {granted,failure->received=granted;assertNull(failure)}
            val block=CocoaGlobalBlock(Pointer(1),callback,"v@?B@")
            completeCocoaBlock(block.pointer,value,Pointer(0))
            assertNull(received)
            queue.removeAt(0).invoke()
            assertEquals(value,received)
        }
    }
    @Test fun anErrorWithoutADescriptionStillPreventsDelivery(){
        val queue=mutableListOf<()->Unit>()
        var failure:Throwable?=null
        val callback=snapshotNotificationAuthorizationCallback({null},{queue+=it}) {_,error->failure=error}
        val block=CocoaGlobalBlock(Pointer(1),callback,"v@?B@")
        completeCocoaBlock(block.pointer,1.toByte(),Pointer(1))
        queue.removeAt(0).invoke()
        assertTrue(failure is IllegalStateException)
    }
    interface PresentationCallback:Callback {fun invoke(block:Pointer,options:Long)}
}
