package com.qingyu.hermescompanion.desktop;
public class ImeCocoa {
    static {System.load(System.getProperty("user.dir")+"/build/imeprobe.dylib");}
    public static native String sources(String select);
    public static native void key(int code, String chars);
}
