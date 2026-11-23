package com.cozyrabbit.audio;

import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.SourceDataLine;
import java.util.Random;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * All sounds are synthesized in code, so the app needs no audio files:
 * a looping rain ambience plus a soft "plop" (fish caught) and a bell chime (pomodoro).
 */
public final class SoundEngine {

    private static final float RATE = 44_100f;
    private static final AudioFormat FORMAT = new AudioFormat(RATE, 16, 1, true, false);

    // a small pool so a splash and a jingle can overlap
    private final ExecutorService effects = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "sfx");
        t.setDaemon(true);
        return t;
    });

    private volatile boolean rainWanted;
    private volatile double rainVolume = 0.6;
    private volatile boolean effectsEnabled = true;
    private Thread rainThread;

    // ------------------------------------------------------------------ rain

    public synchronized void setRain(boolean on) {
        rainWanted = on;
        if (on && (rainThread == null || !rainThread.isAlive())) {
            rainThread = new Thread(this::rainLoop, "rain");
            rainThread.setDaemon(true);
            rainThread.start();
        }
    }

    public void setRainVolume(double volume01) {
        rainVolume = Math.max(0, Math.min(1, volume01));
    }

    public void setEffectsEnabled(boolean on) {
        effectsEnabled = on;
    }

    private void rainLoop() {
        try (SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT)) {
            line.open(FORMAT, 8192);
            line.start();
            Random rnd = new Random();
            byte[] buf = new byte[2048];
            double lp = 0, lp2 = 0, brown = 0, drop = 0, dropLp = 0, gust = 0, gustTarget = 0.5;
            double gain = 0;
            int gustCounter = 0;
            while (true) {
                double target = rainWanted ? rainVolume : 0;
                for (int i = 0; i < buf.length; i += 2) {
                    gain += (target - gain) * 0.00005; // slow fade in/out
                    double white = rnd.nextDouble() * 2 - 1;
                    lp += 0.35 * (white - lp);           // soft hiss
                    lp2 += 0.08 * (lp - lp2);            // body of the rain
                    brown = brown * 0.997 + white * 0.015; // low rumble
                    if (rnd.nextDouble() < 0.0016) {      // individual drops
                        drop += (rnd.nextDouble() * 0.6 + 0.2) * (rnd.nextBoolean() ? 1 : -1);
                    }
                    drop *= 0.93;
                    dropLp += 0.5 * (drop - dropLp);
                    if (++gustCounter > 22_050) {
                        gustCounter = 0;
                        gustTarget = 0.65 + rnd.nextDouble() * 0.35;
                    }
                    gust += (gustTarget - gust) * 0.00003;
                    double sample = (lp * 0.18 + lp2 * 0.55 + brown * 0.9 + dropLp * 0.35) * gust * gain;
                    int v = (int) Math.max(-32767, Math.min(32767, sample * 26_000));
                    buf[i] = (byte) v;
                    buf[i + 1] = (byte) (v >> 8);
                }
                line.write(buf, 0, buf.length);
                if (!rainWanted && gain < 0.0005) {
                    line.drain();
                    return;
                }
            }
        } catch (Exception e) {
            System.err.println("Rain sound unavailable: " + e.getMessage());
        }
    }

    // ------------------------------------------------------------------ effects

    /** Short water "plop" for a caught fish. */
    public void plop() {
        play(() -> {
            int n = (int) (RATE * 0.22);
            double[] s = new double[n];
            double phase = 0;
            for (int i = 0; i < n; i++) {
                double t = i / RATE;
                double freq = 260 + 900 * Math.exp(-t * 28);
                phase += 2 * Math.PI * freq / RATE;
                s[i] = Math.sin(phase) * Math.exp(-t * 18) * 0.55;
            }
            return s;
        });
    }

    /** Rod swinging through the air, then the reel letting line out. */
    public void cast() {
        play(() -> {
            int n = (int) (RATE * 0.75);
            double[] s = new double[n];
            Random rnd = new Random(1);
            double lp = 0;
            for (int i = 0; i < (int) (RATE * 0.32); i++) {
                double t = i / (RATE * 0.32);
                double cutoff = 0.04 + 0.35 * Math.sin(t * Math.PI);
                lp += cutoff * ((rnd.nextDouble() * 2 - 1) - lp);
                s[i] += lp * Math.sin(t * Math.PI) * 0.9;
            }
            addTicks(s, (int) (RATE * 0.22), (int) (RATE * 0.5), 0.012, 0.025, 0.22, rnd);
            return s;
        });
    }

    /** The bobber landing on the water. */
    public void splash() {
        play(() -> {
            int n = (int) (RATE * 0.5);
            double[] s = new double[n];
            Random rnd = new Random(2);
            double lp = 0;
            for (int i = 0; i < n; i++) {
                double t = i / RATE;
                lp += 0.18 * ((rnd.nextDouble() * 2 - 1) - lp);
                s[i] = lp * Math.exp(-t * 9) * 1.1;
            }
            addBubble(s, (int) (RATE * 0.02), 520, 1300, 0.12, 0.35);
            addBubble(s, (int) (RATE * 0.11), 700, 1600, 0.08, 0.2);
            return s;
        });
    }

    /** A bite: quick reeling clicks, a splash and the fish leaving the water. */
    public void reelIn() {
        play(() -> {
            int n = (int) (RATE * 0.9);
            double[] s = new double[n];
            Random rnd = new Random(3);
            addTicks(s, 0, (int) (RATE * 0.55), 0.018, 0.035, 0.28, rnd);
            double lp = 0;
            int start = (int) (RATE * 0.05);
            for (int i = start; i < n; i++) {
                double t = (i - start) / RATE;
                lp += 0.3 * ((rnd.nextDouble() * 2 - 1) - lp);
                s[i] += lp * Math.exp(-t * 7) * 0.9;
            }
            addBubble(s, (int) (RATE * 0.08), 400, 1100, 0.1, 0.35);
            return s;
        });
    }

    /** The fish lands in the bucket: a plop and a tiny happy jingle (longer for rare fish). */
    public void caught(boolean rare) {
        play(() -> {
            int n = (int) (RATE * (rare ? 1.8 : 1.1));
            double[] s = new double[n];
            addBubble(s, 0, 260, 900, 0.22, 0.5);
            double[] notes = rare ? new double[]{783.99, 987.77, 1174.66, 1567.98} : new double[]{880.0, 1318.5};
            for (int k = 0; k < notes.length; k++) {
                addPluck(s, (int) (RATE * (0.12 + k * 0.11)), notes[k], rare ? 0.22 : 0.2);
            }
            return s;
        });
    }

    /** Soft wooden click for buttons. */
    public void click() {
        play(() -> {
            int n = (int) (RATE * 0.06);
            double[] s = new double[n];
            for (int i = 0; i < n; i++) {
                double t = i / RATE;
                s[i] = (Math.sin(2 * Math.PI * 1800 * t) * 0.5 + Math.sin(2 * Math.PI * 950 * t) * 0.5)
                        * Math.exp(-t * 90) * 0.35;
            }
            return s;
        });
    }

    /** Bubble pop, used when the speech bubble over a bucket opens. */
    public void pop() {
        play(() -> {
            double[] s = new double[(int) (RATE * 0.16)];
            addBubble(s, 0, 500, 1400, 0.09, 0.45);
            return s;
        });
    }

    /** Gentle two-note toggle for switching weather or mode. */
    public void toggle() {
        play(() -> {
            double[] s = new double[(int) (RATE * 0.45)];
            addPluck(s, 0, 660, 0.16);
            addPluck(s, (int) (RATE * 0.07), 990, 0.13);
            return s;
        });
    }

    private static void addTicks(double[] s, int from, int to, double minGap, double maxGap, double amp, Random rnd) {
        int i = from;
        while (i < to && i < s.length) {
            for (int k = 0; k < (int) (RATE * 0.004) && i + k < s.length; k++) {
                double t = k / RATE;
                s[i + k] += Math.sin(2 * Math.PI * 3200 * t) * Math.exp(-t * 1400) * amp;
            }
            i += (int) (RATE * (minGap + rnd.nextDouble() * (maxGap - minGap)));
        }
    }

    /** A rising "bloop": the sound of a bubble or something dropping into water. */
    private static void addBubble(double[] s, int offset, double f0, double f1, double length, double amp) {
        double phase = 0;
        int n = (int) (RATE * length);
        for (int i = 0; i < n && offset + i < s.length; i++) {
            double t = i / (double) n;
            double f = f0 + (f1 - f0) * t * t;
            phase += 2 * Math.PI * f / RATE;
            double env = Math.sin(Math.PI * Math.min(1, t * 1.4)) * (1 - t);
            s[offset + i] += Math.sin(phase) * env * amp;
        }
    }

    private static void addPluck(double[] s, int offset, double f, double amp) {
        for (int i = offset; i < s.length; i++) {
            double t = (i - offset) / RATE;
            double env = Math.exp(-t * 6) * Math.min(1, t * 600);
            s[i] += (Math.sin(2 * Math.PI * f * t) + Math.sin(2 * Math.PI * f * 2 * t) * 0.25) * env * amp;
        }
    }

    /** Two soft bell notes for the end of a pomodoro phase. */
    public void chime() {
        play(() -> {
            int n = (int) (RATE * 2.2);
            double[] s = new double[n];
            addBell(s, 0, 659.25);
            addBell(s, (int) (RATE * 0.35), 987.77);
            return s;
        });
    }

    private static void addBell(double[] s, int offset, double f) {
        for (int i = offset; i < s.length; i++) {
            double t = (i - offset) / RATE;
            double env = Math.exp(-t * 2.6) * Math.min(1, t * 400);
            s[i] += (Math.sin(2 * Math.PI * f * t) * 0.6
                    + Math.sin(2 * Math.PI * f * 2.01 * t) * 0.2
                    + Math.sin(2 * Math.PI * f * 3.02 * t) * 0.08) * env * 0.35;
        }
    }

    private interface Synth {
        double[] render();
    }

    private void play(Synth synth) {
        if (!effectsEnabled) {
            return;
        }
        effects.submit(() -> {
            try (SourceDataLine line = AudioSystem.getSourceDataLine(FORMAT)) {
                double[] s = synth.render();
                byte[] out = new byte[s.length * 2];
                for (int i = 0; i < s.length; i++) {
                    int v = (int) Math.max(-32767, Math.min(32767, s[i] * 32767));
                    out[2 * i] = (byte) v;
                    out[2 * i + 1] = (byte) (v >> 8);
                }
                line.open(FORMAT);
                line.start();
                line.write(out, 0, out.length);
                line.drain();
            } catch (Exception e) {
                System.err.println("Sound effect unavailable: " + e.getMessage());
            }
        });
    }

    public void shutdown() {
        rainWanted = false;
        effects.shutdownNow();
    }
}
