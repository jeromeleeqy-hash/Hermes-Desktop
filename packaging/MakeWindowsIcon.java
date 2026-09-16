import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import javax.imageio.ImageIO;

/** Format conversion of the existing Hermes icon; no additional artwork or build dependency. */
class MakeWindowsIcon {
    public static void main(String[] args) throws Exception {
        var source = ImageIO.read(Path.of(args[0]).toFile());
        // Optional atlas rectangle: x,y,width,height. Preserve the selected C1 artwork.
        if (args.length >= 4) {
            String[] rect = args[2].split(",");
            int x=Integer.parseInt(rect[0]), y=Integer.parseInt(rect[1]);
            int w=Integer.parseInt(rect[2]), h=Integer.parseInt(rect[3]);
            var atlas = source;
            source = new BufferedImage(512,512,BufferedImage.TYPE_INT_ARGB);
            var g = source.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.drawImage(atlas,6,6,506,506,x+2,y+2,x+w-2,y+h-2,null);
            var mask = new BufferedImage(512,512,BufferedImage.TYPE_INT_ARGB);
            var m = mask.createGraphics();
            m.setRenderingHint(RenderingHints.KEY_ANTIALIASING,RenderingHints.VALUE_ANTIALIAS_ON);
            m.setColor(java.awt.Color.WHITE);
            m.fill(new java.awt.geom.RoundRectangle2D.Double(6,6,500,500,180,180));
            m.dispose();
            g.setComposite(java.awt.AlphaComposite.DstIn);g.drawImage(mask,0,0,null);g.dispose();
            ImageIO.write(source,"png",Path.of(args[3]).toFile());
        }
        int[] sizes = {16,20,24,32,40,48,64,96,128,256};
        var images = new ArrayList<byte[]>();
        int total = 6 + 16 * sizes.length;
        for (int size : sizes) {
            var image = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
            var graphics = image.createGraphics();
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.drawImage(source, 0, 0, size, size, null);
            graphics.dispose();
            var png = new ByteArrayOutputStream();
            ImageIO.write(image, "png", png);
            images.add(png.toByteArray());
            total += png.size();
        }
        var ico = ByteBuffer.allocate(total).order(ByteOrder.LITTLE_ENDIAN);
        ico.putShort((short)0).putShort((short)1).putShort((short)sizes.length);
        int offset = 6 + 16 * sizes.length;
        for (int i = 0; i < sizes.length; i++) {
            ico.put((byte)sizes[i]).put((byte)sizes[i]).put((byte)0).put((byte)0);
            ico.putShort((short)1).putShort((short)32).putInt(images.get(i).length).putInt(offset);
            offset += images.get(i).length;
        }
        for (var image : images) ico.put(image);
        Files.write(Path.of(args[1]), ico.array());
    }
}
