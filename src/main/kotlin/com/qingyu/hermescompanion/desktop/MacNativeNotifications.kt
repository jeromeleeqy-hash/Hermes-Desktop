package com.qingyu.hermescompanion.desktop

import com.sun.jna.*
import com.sun.jna.Function
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.ConcurrentLinkedQueue

internal interface NotificationResponseCallback:Callback {
    fun invoke(self:Pointer,selector:Pointer,center:Pointer,response:Pointer,completion:Pointer)
}
internal interface NotificationAuthorizationCallback:Callback {fun invoke(block:Pointer,granted:Byte,error:Pointer?)}
internal interface NotificationErrorCallback:Callback {fun invoke(block:Pointer,error:Pointer?)}

/** Apple Blocks ABI (64-bit). The owning singleton retains callbacks and memory for process life. */
internal class CocoaGlobalBlock(isa:Pointer,val callback:Callback,signature:String) {
    private val signatureBytes=signature.toByteArray(Charsets.UTF_8)
    private val signatureMemory=Memory(signatureBytes.size.toLong()+1).apply {write(0,signatureBytes,0,signatureBytes.size);setByte(signatureBytes.size.toLong(),0)}
    val descriptor=Memory(24).apply {clear();setLong(8,32);setPointer(16,signatureMemory)}
    val pointer=Memory(32).apply {
        clear();setPointer(0,isa);setInt(8,(1 shl 28) or (1 shl 30))
        setPointer(16,CallbackReference.getFunctionPointer(callback));setPointer(24,descriptor)
    }
}
internal fun completeCocoaBlock(block:Pointer,vararg args:Any) {
    Function.getFunction(block.getPointer(16)).invokeVoid(arrayOf(block,*args))
}

/** Notifications belong to the running Hermes.app bundle, not osascript/Script Editor. */
internal object MacNativeNotifications {
    private val framework=NativeLibrary.getInstance("/System/Library/Frameworks/UserNotifications.framework/UserNotifications")
    private val objc=NativeLibrary.getInstance("objc")
    private val system=NativeLibrary.getInstance("System")
    private val send=objc.getFunction("objc_msgSend")
    private val selectors=ConcurrentHashMap<String,Pointer>()
    private fun selector(name:String)=selectors.computeIfAbsent(name){objc.getFunction("sel_registerName").invokePointer(arrayOf(it))}
    private fun type(name:String)=requireNotNull(objc.getFunction("objc_getClass").invokePointer(arrayOf(name)))
    private fun pointer(target:Pointer,name:String,vararg args:Any?):Pointer?=send.invokePointer(arrayOf(target,selector(name),*args))
    private fun call(target:Pointer,name:String,vararg args:Any?){send.invokeVoid(arrayOf(target,selector(name),*args))}
    private fun string(value:String)=requireNotNull(pointer(type("NSString"),"stringWithUTF8String:",value))
    private fun text(value:Pointer?)=value?.let {pointer(it,"UTF8String")?.getString(0,"UTF-8")}
    private var center:Pointer?=null
    private var delegate:Pointer?=null
    @Volatile private var activate:((DesktopNotificationTarget)->Unit)?=null
    private val pending=ConcurrentLinkedQueue<Outgoing>()
    private var requesting=false // AppKit main queue only
    private data class Outgoing(val title:String,val body:String,val sound:Boolean,val target:DesktopNotificationTarget)
    private val responseCallback=object:NotificationResponseCallback {
        override fun invoke(self:Pointer,selector:Pointer,center:Pointer,response:Pointer,completion:Pointer) {
            try {
                val defaultAction=framework.getGlobalVariableAddress("UNNotificationDefaultActionIdentifier").getPointer(0)
                if(text(pointer(response,"actionIdentifier"))!=text(defaultAction))return
                val notification=pointer(response,"notification")?:return
                val request=pointer(notification,"request")?:return
                val content=pointer(request,"content")?:return
                val info=pointer(content,"userInfo")?:return
                val target=DesktopNotificationTarget.decode(text(pointer(info,"objectForKey:",string("hermes.route"))).orEmpty())?:return
                activate?.invoke(target)
            }catch(e:Throwable){log(e)}finally {completeCocoaBlock(completion)}
        }
    }
    private val foregroundCallback=object:NotificationResponseCallback {
        override fun invoke(self:Pointer,selector:Pointer,center:Pointer,response:Pointer,completion:Pointer) {
            // UNNotificationPresentationOptionBanner | List | Sound. Content controls whether sound exists.
            completeCocoaBlock(completion,26L)
        }
    }
    private val authorizationCallback=object:NotificationAuthorizationCallback {
        override fun invoke(block:Pointer,granted:Byte,error:Pointer?) {
            MacAppKit.enqueue {
                requesting=false
                if(error!=null)log(IllegalStateException(text(pointer(error,"localizedDescription"))))
                while(true){val message=pending.poll()?:break;if(granted.toInt()!=0&&error==null)deliver(message)}
            }
        }
    }
    private val errorCallback=object:NotificationErrorCallback {
        override fun invoke(block:Pointer,error:Pointer?) {
            if(error!=null)log(IllegalStateException(text(pointer(error,"localizedDescription"))))
        }
    }
    private val blockIsa=system.getGlobalVariableAddress("_NSConcreteGlobalBlock")
    private val authorizationBlock=CocoaGlobalBlock(blockIsa,authorizationCallback,"v@?B@")
    private val errorBlock=CocoaGlobalBlock(blockIsa,errorCallback,"v@?@")
    private fun ensureCenter():Pointer {
        center?.let {return it}
        val bundle=pointer(type("NSBundle"),"mainBundle")!!
        check(text(pointer(bundle,"bundleIdentifier"))=="com.qingyu.hermes.desktop") {"Native notifications require the packaged Hermes.app"}
        val cls=objc.getFunction("objc_allocateClassPair").invokePointer(arrayOf(type("NSObject"),"HermesNotificationDelegate202",0L))
            ?:error("Unable to create notification delegate")
        fun method(name:String,callback:Callback){check(objc.getFunction("class_addMethod").invokeInt(arrayOf(cls,selector(name),CallbackReference.getFunctionPointer(callback),"v@:@@@?"))!=0)}
        method("userNotificationCenter:didReceiveNotificationResponse:withCompletionHandler:",responseCallback)
        method("userNotificationCenter:willPresentNotification:withCompletionHandler:",foregroundCallback)
        val protocol=objc.getFunction("objc_getProtocol").invokePointer(arrayOf("UNUserNotificationCenterDelegate"))
        if(protocol!=null)objc.getFunction("class_addProtocol").invokeInt(arrayOf(cls,protocol))
        objc.getFunction("objc_registerClassPair").invokeVoid(arrayOf(cls))
        delegate=pointer(cls,"new")!!
        return pointer(type("UNUserNotificationCenter"),"currentNotificationCenter")!!.also {center=it;call(it,"setDelegate:",delegate)}
    }
    fun install(onActivate:(DesktopNotificationTarget)->Unit){activate=onActivate;MacAppKit.enqueue {ensureCenter()}}
    fun close(){activate=null}
    fun show(title:String,body:String,sound:Boolean,target:DesktopNotificationTarget) {
        MacAppKit.enqueue {
            val value=ensureCenter();pending+=Outgoing(title,body,sound,target)
            if(!requesting){requesting=true;call(value,"requestAuthorizationWithOptions:completionHandler:",7L,authorizationBlock.pointer)}
        }
    }
    private fun deliver(message:Outgoing) {
        val content=pointer(type("UNMutableNotificationContent"),"new")!!
        try {
            call(content,"setTitle:",string(message.title));call(content,"setBody:",string(message.body))
            if(message.sound)call(content,"setSound:",pointer(type("UNNotificationSound"),"defaultSound"))
            val info=pointer(type("NSDictionary"),"dictionaryWithObject:forKey:",string(message.target.encode()),string("hermes.route"))!!
            call(content,"setUserInfo:",info)
            val request=pointer(type("UNNotificationRequest"),"requestWithIdentifier:content:trigger:",string(UUID.randomUUID().toString()),content,null)!!
            call(ensureCenter(),"addNotificationRequest:withCompletionHandler:",request,errorBlock.pointer)
        }finally {call(content,"release")}
    }
    private fun log(error:Throwable){java.util.logging.Logger.getLogger("Hermes.Notifications").log(java.util.logging.Level.WARNING,"Native notification failed",error)}
}
