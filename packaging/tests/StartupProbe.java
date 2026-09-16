import java.awt.*;
import java.awt.event.*;
import java.io.File;
import javax.imageio.ImageIO;
import javax.swing.SwingUtilities;
public class StartupProbe {
 public static void main(String[] args) {
  Thread probe = new Thread(() -> {
   try {
    Thread.sleep(6500);
    final Window[] app = new Window[1];
    SwingUtilities.invokeAndWait(() -> {
     for (Window w : Window.getWindows())
      if (w instanceof Frame f && f.getTitle().equals("Hermes") && w.isShowing() && w.getWidth()>600) app[0]=w;
    });
    if (app[0] == null) throw new AssertionError("Main window did not become visible");
    Rectangle bounds = app[0].getBounds();
    ImageIO.write(new Robot().createScreenCapture(bounds), "png", new File(args[0]));
    System.out.println("MAIN_WINDOW_READY " + bounds.width + "x" + bounds.height);
    SwingUtilities.invokeAndWait(() -> app[0].dispatchEvent(new WindowEvent(app[0],WindowEvent.WINDOW_CLOSING)));
   } catch(Throwable t) {t.printStackTrace();System.exit(91);}
  }, "startup-verifier");
  probe.setDaemon(true);probe.start();
  com.qingyu.hermescompanion.desktop.MainKt.main(new String[]{"--demo", "--frameless"});
 }
}
