package com.qingyu.hermescompanion.platform;

import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.TimeUnit;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/** Local-only decoding. Never overwrites the source, downloads codecs, or invokes a shell. */
public final class ModernImageSupport {
    private ModernImageSupport() {}
    public static String mime(String name) {
        String ext=name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);
        return switch(ext) {
            case "heic", "heics" -> "image/heic";
            case "heif", "heifs" -> "image/heif";
            case "avif" -> "image/avif";
            case "png" -> "image/png";
            case "jpg", "jpeg" -> "image/jpeg";
            case "webp" -> "image/webp";
            case "gif" -> "image/gif";
            case "bmp" -> "image/bmp";
            default -> null;
        };
    }
    public static boolean isModern(String name) {
        return Set.of("image/heic","image/heif","image/avif").contains(Objects.toString(mime(name),""));
    }
    public static void checkDimensions(long width,long height) throws IOException {
        if(width<=0||height<=0||width>60_000_000L/height)throw new IOException("图片超过 6000 万像素，请用本机图片软件处理。");
    }
    public static synchronized byte[] thumbnail(byte[] source,String name)throws IOException,InterruptedException {
        try(ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
            if(readers.hasNext()) {
                ImageReader reader=readers.next();
                try {
                    reader.setInput(input);int width=reader.getWidth(0),height=reader.getHeight(0);
                    checkDimensions(width,height);
                    int step=Math.max(1,(Math.max(width,height)+95)/96);
                    var options=reader.getDefaultReadParam();options.setSourceSubsampling(step,step,0,0);
                    ByteArrayOutputStream out=new ByteArrayOutputStream();
                    ImageIO.write(reader.read(0,options),"png",out);return out.toByteArray();
                }finally{reader.dispose();}
            }
        }
        if(isModern(name))return thumbnail(png(source,name),"preview.png");
        throw new IOException("本机无法生成缩略图，原图已保留。");
    }
    public static synchronized byte[] png(byte[] source,String name) throws IOException,InterruptedException {
        if(source.length>12*1024*1024)throw new IOException("图片超过 12 MB。");
        // ImageIO readers may be supplied by an already installed codec. Check before allocation.
        try(ImageInputStream input=ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
            Iterator<ImageReader> readers=ImageIO.getImageReaders(input);
            if(readers.hasNext()) {
                ImageReader reader=readers.next();
                try {
                    reader.setInput(input);checkDimensions(reader.getWidth(0),reader.getHeight(0));
                    ByteArrayOutputStream out=new ByteArrayOutputStream();
                    if(!ImageIO.write(reader.read(0),"png",out))throw new IOException("无法生成 PNG 副本。");
                    return bounded(out.toByteArray());
                }finally{reader.dispose();}
            }
        }
        if(!isModern(name))throw new IOException("本机无法解码此图片。");
        Path dir=Files.createTempDirectory("hermes-image-");
        try {
            try{Files.setPosixFilePermissions(dir,java.nio.file.attribute.PosixFilePermissions.fromString("rwx------"));}catch(UnsupportedOperationException ignored){}
            String ext=Objects.requireNonNull(mime(name)).substring(6);
            Path input=dir.resolve("source."+ext),output=dir.resolve("preview.png");
            Files.write(input,source);
            String os=System.getProperty("os.name","").toLowerCase(Locale.ROOT);
            if(os.contains("mac")) {
                String dimensions=run(List.of("/usr/bin/sips","-g","pixelWidth","-g","pixelHeight",input.toString()),dir);
                java.util.regex.Matcher w=java.util.regex.Pattern.compile("pixelWidth:\\s*(\\d+)").matcher(dimensions);
                java.util.regex.Matcher h=java.util.regex.Pattern.compile("pixelHeight:\\s*(\\d+)").matcher(dimensions);
                if(!w.find()||!h.find())throw new IOException("系统未提供图片尺寸，无法安全预览。");
                checkDimensions(Long.parseLong(w.group(1)),Long.parseLong(h.group(1)));
                run(List.of("/usr/bin/sips","-s","format","png",input.toString(),"--out",output.toString()),dir);
            }else if(os.contains("win")) {
                Path script=dir.resolve("decode.ps1");
                Files.writeString(script,"""
                    param([string]$SourceImage,[string]$OutputImage)
                    $ErrorActionPreference='Stop'
                    Add-Type -AssemblyName PresentationCore
                    $stream=[IO.File]::OpenRead($SourceImage)
                    try {
                      $decoder=[Windows.Media.Imaging.BitmapDecoder]::Create($stream,[Windows.Media.Imaging.BitmapCreateOptions]::PreservePixelFormat,[Windows.Media.Imaging.BitmapCacheOption]::OnDemand)
                      $frame=$decoder.Frames[0]
                      if(([long]$frame.PixelWidth*$frame.PixelHeight) -gt 60000000){throw 'Image exceeds pixel limit'}
                      $render=$frame
                      $orientation=1
                      if($frame.Metadata -is [Windows.Media.Imaging.BitmapMetadata]) {
                        try {$value=$frame.Metadata.GetQuery('/app1/ifd/{ushort=274}');if($null -ne $value){$orientation=[int]$value}} catch {}
                      }
                      $matrix=[Windows.Media.Matrix]::Identity
                      switch($orientation) {
                        2 {$matrix.Scale(-1,1)}
                        3 {$matrix.Rotate(180)}
                        4 {$matrix.Scale(1,-1)}
                        5 {$matrix.Scale(-1,1);$matrix.Rotate(270)}
                        6 {$matrix.Rotate(90)}
                        7 {$matrix.Scale(-1,1);$matrix.Rotate(90)}
                        8 {$matrix.Rotate(270)}
                      }
                      if($orientation -gt 1 -and $orientation -le 8) {
                        $transform=New-Object Windows.Media.MatrixTransform -ArgumentList $matrix
                        $render=New-Object Windows.Media.Imaging.TransformedBitmap -ArgumentList $frame,$transform
                      }
                      $encoder=New-Object Windows.Media.Imaging.PngBitmapEncoder
                      $encoder.Frames.Add([Windows.Media.Imaging.BitmapFrame]::Create($render))
                      $out=[IO.File]::Create($OutputImage)
                      try {$encoder.Save($out)} finally {$out.Dispose()}
                    } finally {$stream.Dispose()}
                    """);
                String windowsRoot=System.getenv("SystemRoot");
                if(windowsRoot==null)throw new IOException("无法定位系统图片解码器。");
                String powershell=Path.of(windowsRoot,"System32","WindowsPowerShell","v1.0","powershell.exe").toString();
                run(List.of(powershell,"-NoProfile","-NonInteractive","-File",script.toString(),input.toString(),output.toString()),dir);
            }else throw new IOException("此格式需要 macOS 系统解码器，或 Windows 对应的图片扩展。原图仍可发送或保存。");
            if(!Files.isRegularFile(output)||Files.size(output)>32*1024*1024)throw new IOException("预览副本过大或未生成，原图已保留。");
            return bounded(Files.readAllBytes(output));
        }finally{
            try(var files=Files.walk(dir)){for(Path p:files.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(p);}
        }
    }
    private static byte[] bounded(byte[] bytes)throws IOException {
        if(bytes.length>32*1024*1024)throw new IOException("PNG 副本超过 32 MB，原图已保留。");
        return bytes;
    }
    private static String run(List<String> args,Path dir)throws IOException,InterruptedException {
        Path log=dir.resolve("decoder.log");
        Process process=new ProcessBuilder(args).redirectErrorStream(true).redirectOutput(log.toFile()).start();
        try {
            if(!process.waitFor(20,TimeUnit.SECONDS))throw new IOException("图片解码超时，原图已保留。");
            if(process.exitValue()!=0)throw new IOException("系统无法解码此图片；请检查 HEIF/AVIF 图片扩展或用本机软件转换。原图已保留。");
            return Files.readString(log);
        }finally{if(process.isAlive()){process.destroyForcibly();process.waitFor(2,TimeUnit.SECONDS);}}
    }
}
