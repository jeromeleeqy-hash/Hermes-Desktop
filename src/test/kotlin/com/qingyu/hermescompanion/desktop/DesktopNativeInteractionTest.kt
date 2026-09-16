package com.qingyu.hermescompanion.desktop

import org.junit.Test
import org.junit.Assert.*
import org.junit.Assume.assumeFalse
import java.awt.*
import java.awt.event.InputEvent
import java.awt.image.BufferedImage
import java.nio.file.Files
import java.util.concurrent.FutureTask
import java.util.concurrent.atomic.AtomicInteger
import javax.imageio.ImageIO
import javax.swing.JFileChooser

/** Uses a real AWT window/event queue on a display; no mocked drag callbacks. */
class DesktopNativeInteractionTest {
    private fun <T> edt(action:()->T):T {
        if(EventQueue.isDispatchThread())return action()
        val task=FutureTask<T>{action()};EventQueue.invokeAndWait(task);return task.get()
    }
    private fun await(condition:()->Boolean){repeat(80){if(condition())return;Thread.sleep(25)};assertTrue("Window event timed out",condition())}
    @Test fun titleDragUsesScreenCoordinatesAndDoubleClickTogglesOnce() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val toggles=AtomicInteger()
        val frame=edt {Frame("Hermes drag regression").apply {isUndecorated=true;bounds=Rectangle(180,160,520,340);add(Canvas());isVisible=true}}
        val handler=edt {CaptionDragHandler(frame,{Rectangle(0,0,388,32)},{false},{},{toggles.incrementAndGet()})}
        try {
            val robot=Robot().apply {autoDelay=35};val original=edt {frame.location}
            robot.mouseMove(original.x+120,original.y+15);robot.mousePress(InputEvent.BUTTON1_DOWN_MASK)
            repeat(8){i->robot.mouseMove(original.x+120+(i+1)*15,original.y+15+(i+1)*8)}
            robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK);robot.waitForIdle()
            assertEquals(Point(original.x+120,original.y+64),edt {frame.location})
            val now=edt {frame.location};robot.mouseMove(now.x+100,now.y+15)
            repeat(2){robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)}
            await {toggles.get()==1}
            robot.mouseMove(now.x+490,now.y+15)
            repeat(2){robot.mousePress(InputEvent.BUTTON1_DOWN_MASK);robot.mouseRelease(InputEvent.BUTTON1_DOWN_MASK)}
            robot.waitForIdle();assertEquals(1,toggles.get())
        }finally {edt {handler.close();frame.dispose()}}
    }
    @Test fun imagePickerHasTheActiveAvatarDialogAsOwnerAndReturnsSelectedFile() {
        assumeFalse(GraphicsEnvironment.isHeadless())
        val frame=edt {Frame("Hermes avatar regression").apply {bounds=Rectangle(160,140,520,380);isVisible=true}}
        val dialog=edt {Dialog(frame,"Avatar",Dialog.ModalityType.APPLICATION_MODAL).apply {bounds=Rectangle(200,180,400,300)}}
        val file=Files.createTempFile("hermes-avatar-",".png").toFile()
        ImageIO.write(BufferedImage(32,32,BufferedImage.TYPE_INT_ARGB),"png",file)
        try {
            EventQueue.invokeLater {dialog.isVisible=true}
            await {edt {dialog.isVisible&&KeyboardFocusManager.getCurrentKeyboardFocusManager().activeWindow===dialog}}
            assertSame(dialog,edt {DesktopFiles.pickerOwner()})
            val selected=FutureTask {DesktopFiles.chooseImage()};EventQueue.invokeLater(selected)
            fun chooser(component:Component):JFileChooser? = if(component is JFileChooser)component else (component as? Container)?.components?.firstNotNullOfOrNull {chooser(it)}
            var picker:JFileChooser?=null
            await {edt {picker=Window.getWindows().filter {it.isVisible}.firstNotNullOfOrNull {chooser(it)};picker!=null}}
            edt {assertSame(dialog,javax.swing.SwingUtilities.getWindowAncestor(picker).owner);picker!!.selectedFile=file;picker!!.approveSelection()}
            assertEquals(file,selected.get(4,java.util.concurrent.TimeUnit.SECONDS))
            assertEquals(32,readAvatarImage(file).width)
        }finally {edt {dialog.dispose();frame.dispose()};file.delete()}
    }
}
