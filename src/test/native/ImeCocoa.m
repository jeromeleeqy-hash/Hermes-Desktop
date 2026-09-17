#import <Cocoa/Cocoa.h>
#import <Carbon/Carbon.h>
#import <jni.h>
#import <objc/runtime.h>
static IMP markOriginal,unmarkOriginal,keyOriginal;
static void traceMark(id obj,SEL sel,id text,NSRange selection,NSRange replacement){fprintf(stderr,"NATIVE MARK %s sel=%lu,%lu replace=%lu,%lu\n",[[text description] UTF8String],selection.location,selection.length,replacement.location,replacement.length);((void(*)(id,SEL,id,NSRange,NSRange))markOriginal)(obj,sel,text,selection,replacement);}
static void traceUnmark(id obj,SEL sel){fprintf(stderr,"NATIVE UNMARK\n");((void(*)(id,SEL))unmarkOriginal)(obj,sel);}
static void traceKey(id obj,SEL sel,NSEvent *event){fprintf(stderr,"NATIVE KEY DOWN %d marked=%d\n",event.keyCode,[(id<NSTextInputClient>)obj hasMarkedText]);((void(*)(id,SEL,NSEvent*))keyOriginal)(obj,sel,event);}
static void installTrace(){Class c=NSClassFromString(@"AWTView");if(!c||markOriginal)return;Method m=class_getInstanceMethod(c,@selector(setMarkedText:selectedRange:replacementRange:));markOriginal=method_setImplementation(m,(IMP)traceMark);Method u=class_getInstanceMethod(c,@selector(unmarkText));unmarkOriginal=method_setImplementation(u,(IMP)traceUnmark);Method k=class_getInstanceMethod(c,@selector(keyDown:));keyOriginal=method_setImplementation(k,(IMP)traceKey);}

JNIEXPORT jstring JNICALL Java_com_qingyu_hermescompanion_desktop_ImeCocoa_sources(JNIEnv *env,jclass cls,jstring name) {
 const char *utf=(*env)->GetStringUTFChars(env,name,0);NSString *match=[NSString stringWithUTF8String:utf];(*env)->ReleaseStringUTFChars(env,name,utf);
 __block NSString *result;
 dispatch_sync(dispatch_get_main_queue(),^{
 installTrace();[NSApp activateIgnoringOtherApps:YES];NSMutableString *out=[NSMutableString string];if([match hasPrefix:@"/"]){OSStatus reg=TISRegisterInputSource((CFURLRef)[NSURL fileURLWithPath:match]);[out appendFormat:@"REGISTER %d\n",(int)reg];}CFArrayRef arr=TISCreateInputSourceList(NULL,true);
 for(CFIndex j=0;j<CFArrayGetCount(arr);j++){TISInputSourceRef p=(TISInputSourceRef)CFArrayGetValueAtIndex(arr,j);NSString *pid=(NSString*)TISGetInputSourceProperty(p,kTISPropertyInputSourceID);if([match hasPrefix:pid])TISEnableInputSource(p);}
 for(CFIndex i=0;i<CFArrayGetCount(arr);i++) {TISInputSourceRef s=(TISInputSourceRef)CFArrayGetValueAtIndex(arr,i);NSString *sid=(NSString*)TISGetInputSourceProperty(s,kTISPropertyInputSourceID);NSString *label=(NSString*)TISGetInputSourceProperty(s,kTISPropertyLocalizedName);[out appendFormat:@"%@ %@\n",sid,label];
 if(match.length && [sid isEqualToString:match]) {OSStatus en=TISEnableInputSource(s);CFArrayRef fresh=TISCreateInputSourceList((CFDictionaryRef)@{(NSString*)kTISPropertyInputSourceID:sid},false);OSStatus se=-999;if(CFArrayGetCount(fresh)>0){TISInputSourceRef active=(TISInputSourceRef)CFArrayGetValueAtIndex(fresh,0);se=TISSelectInputSource(active);}[out appendFormat:@"SELECTABLE %@ ENABLED %@\n",TISGetInputSourceProperty(s,kTISPropertyInputSourceIsSelectCapable),TISGetInputSourceProperty(s,kTISPropertyInputSourceIsEnabled)];CFRelease(fresh);[out appendFormat:@"ENABLE %d SELECT %d\n",(int)en,(int)se];}}
 CFRelease(arr);TISInputSourceRef current=TISCopyCurrentKeyboardInputSource();[out appendFormat:@"CURRENT %@\n",TISGetInputSourceProperty(current,kTISPropertyInputSourceID)];CFRelease(current);result=[out copy];});
 jstring res=(*env)->NewStringUTF(env,[result UTF8String]);[result release];return res;
}
JNIEXPORT void JNICALL Java_com_qingyu_hermescompanion_desktop_ImeCocoa_key(JNIEnv *env,jclass cls,jint code,jstring chars) {
 const char *utf=(*env)->GetStringUTFChars(env,chars,0);NSString *str=[NSString stringWithUTF8String:utf];(*env)->ReleaseStringUTFChars(env,chars,utf);
 dispatch_sync(dispatch_get_main_queue(),^{NSWindow *w=[NSApp keyWindow];if(!w)for(NSWindow *candidate in NSApp.windows){if(candidate.isVisible){w=candidate;break;}}[NSApp activateIgnoringOtherApps:YES];[w makeKeyAndOrderFront:nil];NSResponder *target=w.firstResponder;fprintf(stderr,"NATIVE KEY %d window=%ld target=%s\n",code,(long)w.windowNumber,NSStringFromClass([target class]).UTF8String);
 for(NSNumber *type in @[@(NSEventTypeKeyDown),@(NSEventTypeKeyUp)]){NSEvent *e=[NSEvent keyEventWithType:type.integerValue location:NSZeroPoint modifierFlags:0 timestamp:NSProcessInfo.processInfo.systemUptime windowNumber:w.windowNumber context:nil characters:str charactersIgnoringModifiers:str isARepeat:NO keyCode:code];if(type.integerValue==NSEventTypeKeyDown)[target keyDown:e];else [target keyUp:e];}});
}
