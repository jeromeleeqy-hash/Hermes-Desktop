package com.qingyu.hermescompanion.platform;

import java.awt.image.BufferedImage;
import java.io.*;
import java.util.Arrays;
import javax.imageio.ImageIO;

/** Standalone checks can run even before Gradle dependencies are available. */
public final class ModernImageSupportCheck {
    public static void main(String[] args)throws Exception {
        check("image/heic".equals(ModernImageSupport.mime("PHOTO.HEIC")),"HEIC MIME");
        check("image/heif".equals(ModernImageSupport.mime("photo.heif")),"HEIF MIME");
        check("image/avif".equals(ModernImageSupport.mime("photo.avif")),"AVIF MIME");
        check(!ModernImageSupport.isModern("notes.txt"),"non-image");
        BufferedImage image=new BufferedImage(3,2,BufferedImage.TYPE_INT_ARGB);
        image.setRGB(1,1,0x7f246831);
        ByteArrayOutputStream out=new ByteArrayOutputStream();ImageIO.write(image,"png",out);
        byte[] original=out.toByteArray(),copy=original.clone();
        byte[] converted=ModernImageSupport.png(original,"photo.png");
        check(Arrays.equals(original,copy),"original untouched");
        BufferedImage decoded=ImageIO.read(new ByteArrayInputStream(converted));
        check(decoded.getWidth()==3&&decoded.getHeight()==2,"dimensions preserved");
        check(decoded.getRGB(1,1)==image.getRGB(1,1),"RGBA preserved");
        BufferedImage large=new BufferedImage(400,200,BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream largeBytes=new ByteArrayOutputStream();ImageIO.write(large,"png",largeBytes);
        BufferedImage thumb=ImageIO.read(new ByteArrayInputStream(ModernImageSupport.thumbnail(largeBytes.toByteArray(),"large.png")));
        check(thumb.getWidth()<=96&&thumb.getHeight()<=96,"bounded thumbnail");
        try{ModernImageSupport.checkDimensions(Long.MAX_VALUE,2);throw new AssertionError("dimension limit");}catch(IOException expected){}
        try{ModernImageSupport.png(new byte[]{1,2,3},"broken.png");throw new AssertionError("invalid image");}catch(IOException expected){}
        System.out.println("PASS: 10 local image checks (MIME, source preservation, dimensions, RGBA, invalid input/limit). Native HEIC/AVIF decoding not tested.");
    }
    private static void check(boolean value,String description){if(!value)throw new AssertionError(description);}
}
