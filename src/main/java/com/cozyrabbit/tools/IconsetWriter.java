package com.cozyrabbit.tools;

import java.io.File;
import java.io.IOException;

/** Writes the macOS .iconset folder for one icon design: {@code IconsetWriter A target/icon.iconset}. */
public final class IconsetWriter {

    private IconsetWriter() {
    }

    public static void main(String[] args) throws IOException {
        AppIcon.writeIconset(args[0].charAt(0), new File(args[1]));
    }
}
