package com.cozyrabbit.ui;

import com.cozyrabbit.render.Colors;
import com.cozyrabbit.render.PixelCanvas;
import javafx.scene.image.ImageView;
import javafx.scene.image.PixelFormat;
import javafx.scene.image.WritableImage;

/** Small pixel-art icons for the weather buttons, drawn with the same pixel engine as the scene. */
public final class PixelIcons {

    private PixelIcons() {
    }

    private static ImageView toView(PixelCanvas pc, int scale) {
        WritableImage img = new WritableImage(pc.w, pc.h);
        img.getPixelWriter().setPixels(0, 0, pc.w, pc.h, PixelFormat.getIntArgbInstance(), pc.px, 0, pc.w);
        ImageView view = new ImageView(img);
        view.setSmooth(false);
        view.setFitWidth(pc.w * scale);
        view.setFitHeight(pc.h * scale);
        return view;
    }

    public static ImageView sun() {
        PixelCanvas pc = new PixelCanvas(12, 12);
        int ray = Colors.rgb(0xF2A93B);
        for (int i = 0; i < 8; i++) {
            double a = i * Math.PI / 4;
            pc.set((int) Math.round(5.5 + Math.cos(a) * 5), (int) Math.round(5.5 + Math.sin(a) * 5), ray);
        }
        pc.fillCircle(6, 6, 3.4, Colors.rgb(0xF7C948));
        pc.set(5, 4, Colors.rgb(0xFFF1B0));
        return toView(pc, 2);
    }

    private static void cloud(PixelCanvas pc, int base) {
        int dark = Colors.shade(base, 0.8);
        pc.fillCircle(4, 5.5, 2.6, dark);
        pc.fillCircle(7.5, 4.5, 3.2, dark);
        pc.fillRect(2, 5, 9, 3, dark);
        pc.fillCircle(4, 5, 2.2, base);
        pc.fillCircle(7.5, 4, 2.8, base);
        pc.fillRect(2, 5, 9, 2, base);
    }

    public static ImageView rain() {
        PixelCanvas pc = new PixelCanvas(12, 12);
        cloud(pc, Colors.rgb(0xB8C2D0));
        int drop = Colors.rgb(0x4F8FD6);
        pc.set(3, 9, drop);
        pc.set(2, 10, drop);
        pc.set(6, 9, drop);
        pc.set(5, 10, drop);
        pc.set(9, 9, drop);
        pc.set(8, 10, drop);
        return toView(pc, 2);
    }

    public static ImageView snow() {
        PixelCanvas pc = new PixelCanvas(12, 12);
        int c = Colors.rgb(0x7FB2E5);
        int center = Colors.rgb(0xD5E8FA);
        pc.vline(6, 1, 10, c);
        pc.hline(1, 10, 6, c);
        pc.line(2, 2, 10, 10, c);
        pc.line(10, 2, 2, 10, c);
        pc.set(6, 6, center);
        pc.set(6, 1, center);
        pc.set(6, 10, center);
        pc.set(1, 6, center);
        pc.set(10, 6, center);
        return toView(pc, 2);
    }

    public static ImageView fish() {
        return fish(com.cozyrabbit.model.FishType.ORANGE, 2);
    }

    public static ImageView fish(com.cozyrabbit.model.FishType type, int scale) {
        PixelCanvas pc = new PixelCanvas(12, 7);
        pc.sprite(com.cozyrabbit.render.Sprites.FISH.get(type), 1, 1, false);
        return toView(pc, scale);
    }
}
