package com.qingyu.hermescompanion.desktop;

import com.qingyu.hermescompanion.model.*;
import com.qingyu.hermescompanion.storage.SecureConfigStore;
import java.nio.file.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import javax.swing.SwingUtilities;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

/** Opt-in real-model check: only microphone/speaker hardware is substituted.
 * No gateway configuration, cookies, credentials, network client or server exists.
 * Run with the Linux verification classpath and the same Mockito startup agent as tests. */
public final class OfflineVoice201Check {
    private static <T> T edt(Callable<T> work) throws Exception {
        FutureTask<T> task=new FutureTask<>(work);
        SwingUtilities.invokeAndWait(task);return task.get();
    }
    private static void await(DesktopController c) throws Exception {
        long end=System.nanoTime()+TimeUnit.SECONDS.toNanos(90);
        while(System.nanoTime()<end){
            VoicePhase phase=edt(()->c.getVoice().getPhase());
            if(phase==VoicePhase.ERROR)throw new AssertionError(edt(()->c.getVoice().getMessage()));
            if(phase==VoicePhase.IDLE)return;
            Thread.sleep(25);
        }
        throw new AssertionError("Local voice did not finish");
    }
    public static void main(String[] args) throws Exception {
        Path models=Path.of(args[0]).toAbsolutePath();
        Path temp=Files.createTempDirectory("hermes-offline-controller-");
        Files.createSymbolicLink(temp.resolve("voice-models"),models);
        AtomicInteger playedBytes=new AtomicInteger();
        DesktopAudio audio=mock(DesktopAudio.class);
        doAnswer(invocation->{SpeechAudio sound=invocation.getArgument(0);playedBytes.addAndGet(sound.getBytes().length);return null;})
            .when(audio).play(any(SpeechAudio.class),anyFloat());
        doAnswer(invocation->{
            Path wave=Files.createTempFile(temp,"captured-",".wav");
            Files.copy(models.resolve("local-voice-smoke.wav"),wave,StandardCopyOption.REPLACE_EXISTING);
            return wave.toFile();
        }).when(audio).stop();
        DesktopController c=edt(()->new DesktopController(false,new SecureConfigStore(temp,()->new byte[32]),false,audio));
        try {
            edt(()->{
                if(c.getConnected())throw new AssertionError("Expected no gateway connection");
                c.saveVoicePreferences(new VoicePreferences(true,"zh-CN","simplified",false,"automatic","local","local",0,true,false,false,"balanced",1f));
                c.speak("你好，这是本地离线朗读测试。",false);return null;
            });
            await(c);
            if(playedBytes.get()<32000)throw new AssertionError("TTS never reached audio output");
            edt(()->{
                c.getVoice().startCapture(false,true);
                if(c.getVoice().getPhase()!=VoicePhase.LISTENING)throw new AssertionError(c.getVoice().getMessage());
                c.getVoice().stopCapture();return null;
            });
            await(c);
            String transcript=edt(()->c.getVoice().getTranscript());
            if(!transcript.contains("桌面版")||!transcript.contains("语音测试"))throw new AssertionError(transcript);
            if(edt(()->c.getVoiceNotes().isEmpty()||!c.getVoiceNotes().get(0).getCommitted()))throw new AssertionError("Missing committed voice note");
            System.out.println("Offline controller TTS output: "+playedBytes.get()+" bytes; offline settings STT: "+transcript);
        } finally {
            edt(()->{c.close();return null;});
            Files.deleteIfExists(temp.resolve("voice-models"));
            try(var paths=Files.walk(temp)){paths.sorted(java.util.Comparator.reverseOrder()).forEach(path->{try{Files.deleteIfExists(path);}catch(Exception ignored){}});}
        }
    }
}
