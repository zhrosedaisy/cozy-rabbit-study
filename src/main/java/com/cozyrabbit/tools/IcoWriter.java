package com.cozyrabbit.tools;

import javax.imageio.ImageIO;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Writes a Windows .ico file for one icon design: {@code IcoWriter A icon.ico}.
 * The file holds the icon in several sizes, each stored as a PNG (supported since Windows Vista).
 */
public final class IcoWriter {

    private static final int[] SIZES = {16, 24, 32, 48, 64, 128, 256};

    private IcoWriter() {
    }

    public static void main(String[] args) throws IOException {
        char design = args[0].charAt(0);
        File out = new File(args[1]);

        List<byte[]> pngs = new ArrayList<>();
        int total = 0;
        for (int size : SIZES) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            ImageIO.write(AppIcon.image(design, size), "png", bytes);
            pngs.add(bytes.toByteArray());
            total += bytes.size();
        }

        int count = SIZES.length;
        ByteBuffer buf = ByteBuffer.allocate(6 + 16 * count + total).order(ByteOrder.LITTLE_ENDIAN);
        buf.putShort((short) 0).putShort((short) 1).putShort((short) count); // header: type 1 = icon
        int offset = 6 + 16 * count;
        for (int i = 0; i < count; i++) {
            int size = SIZES[i];
            byte dim = (byte) (size >= 256 ? 0 : size); // 0 means 256
            buf.put(dim).put(dim).put((byte) 0).put((byte) 0);
            buf.putShort((short) 1).putShort((short) 32);
            buf.putInt(pngs.get(i).length).putInt(offset);
            offset += pngs.get(i).length;
        }
        for (byte[] png : pngs) {
            buf.put(png);
        }
        Files.write(out.toPath(), buf.array());
    }
}
