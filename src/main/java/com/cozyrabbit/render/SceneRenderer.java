package com.cozyrabbit.render;

import com.cozyrabbit.model.FishType;
import com.cozyrabbit.model.Weather;
import com.cozyrabbit.service.TodayState.BucketView;
import javafx.geometry.VPos;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.scene.text.TextAlignment;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Draws the lake, a pier that recedes towards the horizon, the fishing rabbit and the weather
 * into a 320x240 pixel buffer. Text (popups, bucket name on hover) is drawn on top at full resolution.
 *
 * <p>The pier uses a simple one-point perspective: a point at depth {@code z} (1 = nearest)
 * appears at {@code y = HORIZON + H0 / z}, and the pier edges converge to a vanishing point on the horizon.
 */
public final class SceneRenderer {

    public static final int W = 320;
    public static final int H = 240;

    private static final int HORIZON = 138;
    private static final double H0 = 102;          // screen distance from horizon to the pier's near edge
    private static final double VPX = 210;         // vanishing point x
    private static final double XL0 = -5;          // pier left edge at z = 1
    private static final double XR0 = 145;         // pier right edge at z = 1
    private static final double Z_FAR = 4.2;       // far end of the pier
    private static final double DECK_THICK = 6;    // side face height at z = 1
    private static final double POST_LEN = 24;

    /** Where your rabbit sits alone, and how much closer it moves when friends join the pier. */
    private static final double RABBIT_Z = 1.9;
    /** Rabbits walk this many depth units per second when they move to a new seat. */
    private static final double WALK_SPEED = 0.9;
    private static final double RABBIT_U = 0.86;
    private static final double HOLDER_BEHIND = 0.48; // the rod holder sits a bit in front of the rabbit
    private static final double LANTERN_Z = 3.25;
    private static final double LANTERN_U = 0.07;
    private static final double BUCKET_Z0 = 1.2;
    private static final double BUCKET_DZ = 0.26;
    private static final double BUCKET_U = 0.2;
    private static final double BOBBER_X = 266;
    private static final double BOBBER_Y = 204;

    public enum RabbitMode { DOZING, FISHING, PAUSED, COFFEE }

    /** Everything the renderer needs to know about the current moment. */
    public record Frame(double hour, Weather weather, RabbitMode rabbit, List<BucketView> buckets,
                        int activeIndex, int hoverIndex, long secondsToNextCatch) {
    }

    private final PixelCanvas pc = new PixelCanvas(W, H);
    private final Random rnd = new Random(7);
    private double time;

    /** Moments in the animation that deserve a sound. */
    public interface SoundCues {
        void cast();

        void splash();

        void reelIn();

        void landed(FishType type);
    }

    private SoundCues sounds = new SoundCues() {
        @Override
        public void cast() {
        }

        @Override
        public void splash() {
        }

        @Override
        public void reelIn() {
        }

        @Override
        public void landed(FishType type) {
        }
    };

    // sky state
    private double skyLight = 1;
    private double warmth;
    private int skyTop;
    private int skyBottom;
    private double hourNow;

    // particles
    private final List<double[]> rain = new ArrayList<>();      // x, y, speed, targetY, len
    private final List<double[]> snow = new ArrayList<>();      // x, y, speed, targetY, phase, size
    private final List<double[]> ripples = new ArrayList<>();   // x, y, age, maxRadius
    private final List<double[]> drops = new ArrayList<>();     // x, y, vx, vy, age
    private final List<double[]> sparkles = new ArrayList<>();  // x, y, age, gold
    private final List<double[]> steam = new ArrayList<>();     // x, y, age, phase
    private final List<double[]> fireflies = new ArrayList<>(); // x, y, vx, vy, phase
    private final List<double[]> birds = new ArrayList<>();     // x, y, speed, phase
    private final List<Cloud> clouds = new ArrayList<>();
    private final double[][] stars = new double[90][];
    private Weather cloudWeather;
    private double nextBirdAt = 4;
    private double steamTimer;

    // overlay particles
    private final List<double[]> zzz = new ArrayList<>();
    private final List<Popup> popups = new ArrayList<>();
    private double zzzTimer;

    // character state
    private RabbitMode lastMode;
    private double castT = 99;
    private double blinkTimer = 3;
    private double reelT = 99;
    private final Map<Long, Integer> shownFish = new HashMap<>();
    private double rabbitZ = RABBIT_Z;
    private boolean meWalking;
    private int hop;
    private Flight flight;

    // bucket screen rectangles from the last render: x, y, w, h
    private final List<int[]> bucketRects = new ArrayList<>();

    // landscape
    private final int[] farRidge = new int[W];
    private final int[] nearRidge = new int[W];
    private final List<int[]> trees = new ArrayList<>();

    private record Popup(String text, double x, double y, boolean rare, double[] age, boolean toast) {
    }

    private static final class Flight {
        FishType type;
        long subjectId;
        int bucketIndex;
        double t;
        double x;
        double y;
    }

    private static final class Cloud {
        double x;
        double y;
        double speed;
        double[][] puffs;
        double width;
        boolean dark;
    }

    public SceneRenderer() {
        for (int i = 0; i < stars.length; i++) {
            stars[i] = new double[]{rnd.nextInt(W), rnd.nextInt(HORIZON - 25), rnd.nextDouble() * 6.28, rnd.nextDouble()};
        }
        for (int x = 0; x < W; x++) {
            farRidge[x] = (int) (HORIZON - 22 - (Math.sin(x * 0.021 + 1.0) * 10 + Math.sin(x * 0.057 + 2.0) * 5
                    + Math.sin(x * 0.13 + 0.3) * 2));
            nearRidge[x] = (int) (HORIZON - 7 - (Math.sin(x * 0.034 + 0.5) * 3.5 + Math.sin(x * 0.091 + 1.3) * 2));
        }
        for (int x = 2; x < W; x += 4 + rnd.nextInt(6)) {
            if (x > 252 && x < 282) {
                continue; // room for the cabin
            }
            trees.add(new int[]{x, 4 + rnd.nextInt(6)});
        }
        for (int i = 0; i < 12; i++) {
            fireflies.add(new double[]{rnd.nextDouble() * W, 110 + rnd.nextDouble() * 70, 0, 0, rnd.nextDouble() * 6.28});
        }
    }

    public void setSoundCues(SoundCues cues) {
        sounds = cues;
    }

    // ===================================================================== perspective

    private static double yAt(double z) {
        return HORIZON + H0 / z;
    }

    private static double leftAt(double z) {
        return VPX + (XL0 - VPX) / z;
    }

    private static double rightAt(double z) {
        return VPX + (XR0 - VPX) / z;
    }

    private static double xAt(double u, double z) {
        double l = leftAt(z);
        return l + (rightAt(z) - l) * u;
    }

    private static double zAtY(double y) {
        return H0 / (y - HORIZON);
    }

    private static boolean onDeck(double x, double y) {
        if (y <= HORIZON) {
            return false;
        }
        double z = zAtY(y);
        return z <= Z_FAR && x >= leftAt(z) && x <= rightAt(z);
    }

    private static double bucketZ(int i) {
        return BUCKET_Z0 + i * BUCKET_DZ;
    }

    private static double bucketScale(int i) {
        return 1.5 / bucketZ(i);
    }

    private int rabbitBaseX() {
        return (int) Math.round(xAt(RABBIT_U, rabbitZ));
    }

    private int rabbitBaseY() {
        return (int) Math.round(yAt(rabbitZ));
    }

    // ===================================================================== update

    public void update(double dt, Frame f) {
        dt = Math.min(dt, 0.1);
        time += dt;
        computeSky(f.hour(), f.weather());
        if (cloudWeather != f.weather()) {
            buildClouds(f.weather());
        }
        if (lastMode != f.rabbit()) {
            if (f.rabbit() == RabbitMode.FISHING) {
                castT = 0;
                if (lastMode != null) {
                    sounds.cast();
                }
            }
            lastMode = f.rabbit();
        }
        double before = castT;
        castT += dt;
        if (f.rabbit() == RabbitMode.FISHING && before < 1.2 && castT >= 1.2) {
            ripples.add(new double[]{BOBBER_X, BOBBER_Y + 1, 0, 8});
            sounds.splash();
        }
        reelT += dt;
        blinkTimer -= dt;
        if (blinkTimer < -0.15) {
            blinkTimer = 2.5 + rnd.nextDouble() * 3;
        }
        updateCatches(dt, f);
        updateFriends(dt);
        // Alone you sit at your usual spot. In a room everybody shares the pier evenly:
        // N rabbits split it into N + 1 equal parts, you in the first seat.
        double targetZ = friends.isEmpty() ? RABBIT_Z : seatZ(0, friends.size() + 1);
        boolean wasWalking = meWalking;
        rabbitZ = walkToward(rabbitZ, targetZ, dt);
        meWalking = rabbitZ != targetZ;
        hop = meWalking ? (int) Math.round(Math.abs(Math.sin(time * 11)) * 2) : 0;
        if (wasWalking && !meWalking && f.rabbit() == RabbitMode.FISHING) {
            castT = 0;
            sounds.cast();
        }
        updateWeather(dt, f);
        updateAmbient(dt, f);
    }

    private void updateCatches(double dt, Frame f) {
        List<BucketView> buckets = f.buckets();
        Map<Long, Integer> seen = new HashMap<>();
        for (int i = 0; i < buckets.size(); i++) {
            BucketView b = buckets.get(i);
            long id = b.subject().id();
            int actual = b.fish().size();
            seen.put(id, actual);
            Integer shown = shownFish.get(id);
            boolean inFlight = flight != null && flight.subjectId == id;
            if (shown == null || actual < shown || actual - shown > 1) {
                shownFish.put(id, inFlight ? actual - 1 : actual);
            } else if (actual == shown + 1 && flight == null) {
                startFlight(b, i);
            }
        }
        shownFish.keySet().retainAll(seen.keySet());

        if (flight != null) {
            flight.t += dt / 1.2;
            if (flight.t >= 1 || flight.bucketIndex >= buckets.size()) {
                land();
            } else {
                double[] target = bucketTop(flight.bucketIndex);
                double t = flight.t;
                double tx = target[0] - 5;
                double ty = target[1] - 6;
                flight.x = BOBBER_X + (tx - BOBBER_X) * t;
                flight.y = BOBBER_Y + (ty - BOBBER_Y) * t - Math.sin(t * Math.PI) * 60;
            }
        }
    }

    private double[] bucketTop(int i) {
        double z = bucketZ(i);
        double s = 1.5 / z;
        return new double[]{xAt(BUCKET_U, z), yAt(z) - 13 * s};
    }

    private void startFlight(BucketView b, int index) {
        flight = new Flight();
        flight.type = b.fish().get(b.fish().size() - 1);
        flight.subjectId = b.subject().id();
        flight.bucketIndex = index;
        flight.x = BOBBER_X;
        flight.y = BOBBER_Y;
        reelT = 0;
        for (int i = 0; i < 12; i++) {
            drops.add(new double[]{BOBBER_X, BOBBER_Y, (rnd.nextDouble() - 0.5) * 55, -35 - rnd.nextDouble() * 50, 0});
        }
        ripples.add(new double[]{BOBBER_X, BOBBER_Y + 1, 0, 11});
        sounds.reelIn();
    }

    private void land() {
        Flight fl = flight;
        flight = null;
        shownFish.merge(fl.subjectId, 1, Integer::sum);
        double[] top = bucketTop(fl.bucketIndex);
        for (int i = 0; i < (fl.type.rare() ? 16 : 7); i++) {
            sparkles.add(new double[]{top[0] + (rnd.nextDouble() - 0.5) * 20, top[1] - 2 - rnd.nextDouble() * 10,
                    rnd.nextDouble() * -0.3, fl.type.rare() ? 1 : 0});
        }
        String text = switch (fl.type) {
            case GOLDEN -> "Golden fish!";
            case KOI -> "Legendary koi!";
            default -> "+1 " + fl.type.label().toLowerCase();
        };
        popups.add(new Popup(text, top[0], top[1] - 12, fl.type.rare(), new double[]{0}, false));
        sounds.landed(fl.type);
    }

    private void updateWeather(double dt, Frame f) {
        int wantRain = f.weather() == Weather.RAINY ? 320 : 0;
        while (rain.size() < wantRain) {
            rain.add(newRainDrop(true));
        }
        while (rain.size() > wantRain) {
            rain.remove(rain.size() - 1);
        }
        for (int i = 0; i < rain.size(); i++) {
            double[] d = rain.get(i);
            d[1] += d[2] * dt;
            d[0] -= d[2] * dt * 0.22;
            if (d[1] >= d[3]) {
                if (d[3] > HORIZON + 2) {
                    if (onDeck(d[0], d[3])) {
                        if (rnd.nextDouble() < 0.35) {
                            drops.add(new double[]{d[0], d[3], (rnd.nextDouble() - 0.5) * 20, -15, 0.3});
                        }
                    } else if (rnd.nextDouble() < 0.45) {
                        double depth = (d[3] - HORIZON) / (H - HORIZON);
                        ripples.add(new double[]{d[0], d[3], 0, 1.5 + depth * 4});
                    }
                }
                rain.set(i, newRainDrop(false));
            }
        }

        int wantSnow = f.weather() == Weather.SNOWY ? 210 : 0;
        while (snow.size() < wantSnow) {
            snow.add(newFlake(true));
        }
        while (snow.size() > wantSnow) {
            snow.remove(snow.size() - 1);
        }
        for (int i = 0; i < snow.size(); i++) {
            double[] s = snow.get(i);
            s[1] += s[2] * dt;
            s[0] += Math.sin(time * 1.3 + s[4]) * 6 * dt - 2 * dt;
            if (s[1] >= s[3]) {
                snow.set(i, newFlake(false));
            }
        }

        ageAll(ripples, dt, 2, 0.7);
        for (Iterator<double[]> it = drops.iterator(); it.hasNext(); ) {
            double[] d = it.next();
            d[0] += d[2] * dt;
            d[1] += d[3] * dt;
            d[3] += 170 * dt;
            d[4] += dt;
            if (d[4] > 0.9) {
                it.remove();
            }
        }
        ageAll(sparkles, dt, 2, 0.8);
    }

    private void updateAmbient(double dt, Frame f) {
        for (Cloud c : clouds) {
            c.x += c.speed * dt;
            if (c.x - c.width > W + 10) {
                c.x = -c.width - 10;
            }
        }
        boolean birdsOk = f.weather() == Weather.SUNNY && skyLight > 0.6;
        if (birdsOk && time > nextBirdAt) {
            int n = 2 + rnd.nextInt(3);
            double y = 40 + rnd.nextDouble() * 40;
            for (int i = 0; i < n; i++) {
                birds.add(new double[]{-8 - i * 9, y + (i % 2) * 4 + rnd.nextDouble() * 2,
                        14 + rnd.nextDouble() * 4, rnd.nextDouble() * 6});
            }
            nextBirdAt = time + 14 + rnd.nextDouble() * 18;
        }
        for (Iterator<double[]> it = birds.iterator(); it.hasNext(); ) {
            double[] b = it.next();
            b[0] += b[2] * dt;
            if (b[0] > W + 10) {
                it.remove();
            }
        }
        for (double[] ff : fireflies) {
            ff[2] = Math.max(-6, Math.min(6, ff[2] + (rnd.nextDouble() - 0.5) * 20 * dt));
            ff[3] = Math.max(-4, Math.min(4, ff[3] + (rnd.nextDouble() - 0.5) * 20 * dt));
            ff[0] += ff[2] * dt;
            ff[1] += ff[3] * dt;
            if (ff[0] < 0) {
                ff[0] += W;
            }
            if (ff[0] > W) {
                ff[0] -= W;
            }
            if (ff[1] < 105 || ff[1] > 185) {
                ff[3] = -ff[3];
            }
        }
        if (f.rabbit() == RabbitMode.COFFEE) {
            steamTimer -= dt;
            if (steamTimer <= 0) {
                double[] mug = mugPos();
                steam.add(new double[]{mug[0] + 2 + rnd.nextInt(3), mug[1] - 1, 0, rnd.nextDouble() * 6});
                steamTimer = 0.28;
            }
        }
        for (Iterator<double[]> it = steam.iterator(); it.hasNext(); ) {
            double[] s = it.next();
            s[2] += dt;
            s[1] -= 5 * dt;
            s[0] += Math.sin(time * 2 + s[3]) * 3 * dt;
            if (s[2] > 2.2) {
                it.remove();
            }
        }
        if (f.rabbit() == RabbitMode.DOZING) {
            zzzTimer -= dt;
            if (zzzTimer <= 0) {
                zzz.add(new double[]{rabbitBaseX() + 8, headY(RabbitMode.DOZING) + 1, 0});
                zzzTimer = 1.3;
            }
        }
        for (Iterator<double[]> it = zzz.iterator(); it.hasNext(); ) {
            double[] z = it.next();
            z[2] += dt;
            z[0] += 4 * dt;
            z[1] -= 6 * dt;
            if (z[2] > 2.6) {
                it.remove();
            }
        }
        final double step = dt;
        popups.removeIf(p -> (p.age()[0] += step) > (p.toast() ? 4.0 : (p.rare() ? 2.6 : 1.6)));
    }

    private static void ageAll(List<double[]> list, double dt, int ageIndex, double maxAge) {
        list.removeIf(p -> (p[ageIndex] += dt) > maxAge);
    }

    private double[] newRainDrop(boolean anywhere) {
        double target = HORIZON + 1 + rnd.nextDouble() * (H - HORIZON);
        return new double[]{rnd.nextDouble() * (W + 70), anywhere ? rnd.nextDouble() * target : -rnd.nextDouble() * 30,
                190 + rnd.nextDouble() * 70, target, 3 + rnd.nextInt(3)};
    }

    private double[] newFlake(boolean anywhere) {
        double target = HORIZON + rnd.nextDouble() * (H - HORIZON);
        return new double[]{rnd.nextDouble() * (W + 30), anywhere ? rnd.nextDouble() * target : -rnd.nextDouble() * 20,
                9 + rnd.nextDouble() * 14, target, rnd.nextDouble() * 6.28, rnd.nextDouble() < 0.25 ? 2 : 1};
    }

    private void buildClouds(Weather w) {
        cloudWeather = w;
        clouds.clear();
        int n = w == Weather.SUNNY ? 6 : 13;
        for (int i = 0; i < n; i++) {
            Cloud c = new Cloud();
            c.width = 22 + rnd.nextDouble() * (w == Weather.SUNNY ? 28 : 44);
            c.x = rnd.nextDouble() * (W + 60) - 30;
            c.y = (w == Weather.SUNNY ? 24 : 12) + rnd.nextDouble() * (w == Weather.SUNNY ? 60 : 70);
            c.speed = 1.2 + rnd.nextDouble() * 2.5;
            c.dark = w != Weather.SUNNY && rnd.nextDouble() < 0.6;
            int puffs = 3 + (int) (c.width / 9);
            c.puffs = new double[puffs][];
            for (int p = 0; p < puffs; p++) {
                double px = (p + 0.5) / puffs * c.width;
                double edge = 1 - Math.abs(px / c.width - 0.5) * 1.4;
                double r = 3.5 + edge * (4 + rnd.nextDouble() * 4);
                c.puffs[p] = new double[]{px, -r * 0.55 + rnd.nextDouble() * 1.5, r};
            }
            clouds.add(c);
        }
    }

    // ===================================================================== sky colors

    private static final double[] KEY_HOURS = {0, 5, 6.5, 8, 17, 19, 20.5, 22, 24};
    private static final int[] KEY_TOP = {0x0B1026, 0x0B1026, 0x4B5D9E, 0x5FAEE0, 0x5FAEE0, 0x46518F, 0x23285A, 0x0B1026, 0x0B1026};
    private static final int[] KEY_BOTTOM = {0x26305A, 0x26305A, 0xF7B98B, 0xCFEFF7, 0xCFEFF7, 0xF38E5C, 0x8A5F86, 0x26305A, 0x26305A};
    private static final double[] KEY_LIGHT = {0.12, 0.12, 0.62, 1, 1, 0.68, 0.36, 0.12, 0.12};
    private static final double[] KEY_WARM = {0, 0, 0.7, 0, 0.05, 1, 0.45, 0, 0};

    private void computeSky(double hour, Weather w) {
        hour = ((hour % 24) + 24) % 24;
        hourNow = hour;
        int k = 0;
        while (k < KEY_HOURS.length - 2 && hour >= KEY_HOURS[k + 1]) {
            k++;
        }
        double t = (hour - KEY_HOURS[k]) / (KEY_HOURS[k + 1] - KEY_HOURS[k]);
        t = t * t * (3 - 2 * t);
        skyTop = Colors.mix(Colors.rgb(KEY_TOP[k]), Colors.rgb(KEY_TOP[k + 1]), t);
        skyBottom = Colors.mix(Colors.rgb(KEY_BOTTOM[k]), Colors.rgb(KEY_BOTTOM[k + 1]), t);
        skyLight = KEY_LIGHT[k] + (KEY_LIGHT[k + 1] - KEY_LIGHT[k]) * t;
        warmth = KEY_WARM[k] + (KEY_WARM[k + 1] - KEY_WARM[k]) * t;
        if (w == Weather.RAINY) {
            int overcast = Colors.mix(Colors.rgb(0x1C212C), Colors.rgb(0x8E98A6), skyLight);
            skyTop = Colors.mix(skyTop, Colors.shade(overcast, 0.85), 0.7);
            skyBottom = Colors.mix(skyBottom, Colors.lighten(overcast, 0.12), 0.68);
            warmth *= 0.3;
        } else if (w == Weather.SNOWY) {
            int overcast = Colors.mix(Colors.rgb(0x252B3D), Colors.rgb(0xC4CEDC), skyLight);
            skyTop = Colors.mix(skyTop, Colors.shade(overcast, 0.92), 0.6);
            skyBottom = Colors.mix(skyBottom, Colors.lighten(overcast, 0.15), 0.6);
            warmth *= 0.4;
        }
    }

    private double nightness() {
        return 1 - skyLight;
    }

    // ===================================================================== render

    public int[] render(Frame f) {
        computeSky(f.hour(), f.weather());
        pc.clearMask();
        pc.setMarking(false);
        drawSky(f);
        drawLandscape(f);
        drawSea(f);

        pc.setMarking(true);
        drawPier(f);
        drawObjects(f);
        drawFlight();
        pc.setMarking(false);

        grade(f.weather());
        drawLights(f);
        drawParticles();
        return pc.px;
    }

    /** Color of the top sky row, used to extend the sky when the window is tall. */
    public int topColor() {
        return pc.px[0];
    }

    private void drawSky(Frame f) {
        for (int y = 0; y < HORIZON; y++) {
            int c = skyColorAt(y);
            for (int x = 0; x < W; x++) {
                pc.px[y * W + x] = c;
            }
        }
        boolean clear = f.weather() == Weather.SUNNY;
        double starAlpha = Math.pow(Math.max(0, nightness() - 0.3) / 0.7, 1.2) * (clear ? 1 : 0.12);
        if (starAlpha > 0.01) {
            for (double[] s : stars) {
                double tw = 0.55 + 0.45 * Math.sin(time * (1.2 + s[3] * 2) + s[2]);
                pc.set((int) s[0], (int) s[1], Colors.withAlpha(0xFFF4F0DC, starAlpha * tw));
                if (s[3] > 0.88 && tw > 0.8) {
                    int dim = Colors.withAlpha(0xFFF4F0DC, starAlpha * 0.4);
                    pc.set((int) s[0] - 1, (int) s[1], dim);
                    pc.set((int) s[0] + 1, (int) s[1], dim);
                    pc.set((int) s[0], (int) s[1] - 1, dim);
                    pc.set((int) s[0], (int) s[1] + 1, dim);
                }
            }
        }
        double cover = clear ? 1 : 0.25;
        double[] sun = sunPos();
        if (sun != null) {
            int sunColor = Colors.mix(Colors.rgb(0xFFF1B8), Colors.rgb(0xFF9A5A), warmth);
            pc.glow(sun[0], sun[1], 28, sunColor, 0.35 * cover);
            if (cover > 0.5) {
                pc.fillCircle(sun[0], sun[1], 8, Colors.lighten(sunColor, 0.3));
                pc.fillCircle(sun[0] - 1, sun[1] - 1, 5.5, Colors.lighten(sunColor, 0.6));
            } else {
                pc.fillCircle(sun[0], sun[1], 8, Colors.withAlpha(Colors.lighten(sunColor, 0.5), 0.35));
            }
        }
        double[] moon = moonPos();
        if (moon != null) {
            double a = Math.min(1, nightness() * 1.6) * (cover > 0.5 ? 1 : 0.3);
            pc.glow(moon[0], moon[1], 22, Colors.rgb(0xC9D4FF), 0.22 * a);
            pc.fillCircle(moon[0], moon[1], 7, Colors.withAlpha(0xFFF3EED8, a));
            pc.fillCircle(moon[0] + 3, moon[1] - 2, 6, Colors.withAlpha(skyColorAt((int) moon[1]), a));
            pc.set((int) moon[0] - 3, (int) moon[1] + 1, Colors.withAlpha(0xFFD9D2B8, a));
            pc.set((int) moon[0] - 2, (int) moon[1] + 3, Colors.withAlpha(0xFFD9D2B8, a));
        }
        drawClouds(f);
        for (double[] b : birds) {
            boolean up = Math.sin(time * 9 + b[3]) > 0;
            int c = Colors.withAlpha(Colors.mix(Colors.rgb(0x2A2A35), skyBottom, 0.25), 0.9);
            int x = (int) b[0];
            int y = (int) b[1];
            pc.set(x, y, c);
            pc.set(x - 1, y - 1, c);
            pc.set(x + 1, y - 1, c);
            pc.set(x - 2, up ? y - 2 : y, c);
            pc.set(x + 2, up ? y - 2 : y, c);
        }
    }

    private int skyColorAt(int y) {
        double t = Math.max(0, Math.min(1, y / (double) HORIZON));
        return Colors.mix(skyTop, skyBottom, Math.pow(t, 1.4));
    }

    private double[] sunPos() {
        double h = hourNow;
        if (h < 5.8 || h > 20.2) {
            return null;
        }
        double p = (h - 5.8) / 14.4;
        return new double[]{24 + p * 272, HORIZON - 8 - Math.sin(p * Math.PI) * 100};
    }

    private double[] moonPos() {
        double h = (hourNow + 24 - 19.3) % 24;
        if (h > 11.2) {
            return null;
        }
        double p = h / 11.2;
        return new double[]{30 + p * 262, HORIZON - 10 - Math.sin(p * Math.PI) * 92};
    }

    private void drawClouds(Frame f) {
        int base = Colors.mix(Colors.mix(Colors.rgb(0x3A4166), Colors.rgb(0xFFFFFF), skyLight), Colors.rgb(0xF7C3A8), warmth * 0.6);
        for (Cloud c : clouds) {
            int main = c.dark ? Colors.mix(base, Colors.rgb(0x5D6573), 0.55 + 0.2 * (1 - skyLight)) : base;
            if (f.weather() != Weather.SUNNY && !c.dark) {
                main = Colors.mix(main, Colors.rgb(0x9AA3B0), 0.35);
            }
            int shadow = Colors.mix(main, skyTop, 0.35);
            int light = Colors.lighten(main, 0.25);
            double alpha = f.weather() == Weather.SUNNY ? 0.92 : 0.97;
            drawCloudLayer(c, 0, 1.2, 0, Colors.withAlpha(shadow, alpha));
            drawCloudLayer(c, 0, 0, 0, Colors.withAlpha(main, alpha));
            drawCloudLayer(c, -0.6, -1.4, -1.6, Colors.withAlpha(light, alpha * 0.9));
        }
    }

    private void drawCloudLayer(Cloud c, double dx, double dy, double dr, int color) {
        for (int y = (int) (c.y - 14); y <= (int) (c.y + 2); y++) {
            if (y > c.y + 1 + dy) {
                continue;
            }
            for (int x = (int) (c.x - 12); x <= (int) (c.x + c.width + 12); x++) {
                for (double[] p : c.puffs) {
                    double ex = x + 0.5 - (c.x + p[0] + dx);
                    double ey = y + 0.5 - (c.y + p[1] + dy);
                    double r = p[2] + dr;
                    if (r > 0 && ex * ex + ey * ey <= r * r) {
                        pc.set(x, y, color);
                        break;
                    }
                }
            }
        }
    }

    private void drawLandscape(Frame f) {
        boolean snowy = f.weather() == Weather.SNOWY;
        int far = Colors.mix(skyBottom, Colors.mix(Colors.rgb(0x1D2240), Colors.rgb(0x5A6B9A), skyLight), 0.55);
        int farShade = Colors.mix(far, skyTop, 0.25);
        int cap = Colors.mix(far, Colors.rgb(0xF2F5FA), snowy ? 0.85 : 0.55);
        for (int x = 0; x < W; x++) {
            int top = farRidge[x];
            for (int y = top; y < HORIZON; y++) {
                int depth = y - top;
                int c = (x + y) % 7 == 0 || depth > 10 ? farShade : far;
                if (top < HORIZON - 24 && depth < (snowy ? 6 : 3) + (x % 3 == 0 ? 1 : 0)) {
                    c = cap;
                }
                pc.px[y * W + x] = Colors.mix(c, skyBottom, 0.15 + 0.25 * depth / 30.0);
            }
        }
        int hill = snowy ? Colors.mix(Colors.rgb(0x4B5468), Colors.rgb(0xE6ECF4), skyLight)
                : Colors.mix(Colors.rgb(0x16202A), Colors.rgb(0x4B7356), skyLight);
        hill = Colors.mix(hill, skyBottom, 0.18);
        int tree = snowy ? Colors.mix(Colors.rgb(0x101820), Colors.rgb(0x2F4A3E), skyLight)
                : Colors.mix(Colors.rgb(0x0E1620), Colors.rgb(0x2B4B37), skyLight);
        int treeLight = Colors.lighten(tree, 0.15);
        for (int x = 0; x < W; x++) {
            for (int y = nearRidge[x]; y < HORIZON; y++) {
                pc.px[y * W + x] = (y == nearRidge[x] && snowy) ? Colors.lighten(hill, 0.3) : hill;
            }
        }
        for (int[] t : trees) {
            int x = t[0];
            int base = nearRidge[Math.min(W - 1, x)] + 1;
            int th = t[1];
            for (int i = 0; i < th; i++) {
                int half = (int) Math.round(i * 0.45);
                int y = base - th + i;
                for (int dx = -half; dx <= half; dx++) {
                    pc.set(x + dx, y, dx < 0 ? treeLight : tree);
                }
                if (snowy && i % 3 == 1) {
                    pc.set(x - half, y, Colors.mix(Colors.rgb(0xE8EEF6), tree, 0.25 + 0.5 * nightness()));
                }
            }
            pc.set(x, base, Colors.shade(tree, 0.7));
        }
        int cx = 262;
        int cy = nearRidge[cx + 6] - 6;
        int wall = Colors.mix(Colors.rgb(0x2A1C18), Colors.rgb(0x8C5A3C), skyLight);
        int roof = snowy ? Colors.mix(Colors.rgb(0x5B6378), Colors.rgb(0xF0F3F8), skyLight)
                : Colors.mix(Colors.rgb(0x2A1414), Colors.rgb(0x9A3B2E), skyLight);
        pc.fillRect(cx, cy, 12, 7, wall);
        for (int i = 0; i < 5; i++) {
            pc.hline(cx - 1 + i, cx + 12 - i, cy - 1 - i, roof);
        }
        pc.fillRect(cx + 9, cy - 7, 2, 4, Colors.shade(wall, 0.8));
        int window = Colors.mix(Colors.rgb(0x3A3428), Colors.rgb(0x6F7A80), skyLight);
        pc.fillRect(cx + 3, cy + 2, 2, 2, window);
        pc.fillRect(cx + 7, cy + 2, 2, 2, window);
        for (int i = 0; i < 6; i++) {
            double t = (time * 0.35 + i / 6.0) % 1.0;
            pc.set((int) (cx + 10 + Math.sin(t * 5 + i) * 1.5 + t * 6), (int) (cy - 8 - t * 16),
                    Colors.withAlpha(Colors.mix(Colors.rgb(0x8C93A0), Colors.rgb(0xE8ECF0), skyLight), 0.55 * (1 - t)));
        }
    }

    private int seaColorAt(int y, Weather w) {
        double t = (y - HORIZON) / (double) (H - HORIZON);
        int deep = Colors.mix(Colors.rgb(0x0B1630), Colors.rgb(0x2A6C8C), skyLight);
        if (w == Weather.RAINY) {
            deep = Colors.mix(deep, Colors.rgb(0x3B4A58), 0.45);
        } else if (w == Weather.SNOWY) {
            deep = Colors.mix(deep, Colors.rgb(0x4A6478), 0.3);
        }
        int top = Colors.mix(skyBottom, deep, 0.45);
        return Colors.mix(top, Colors.shade(deep, 0.75), Math.pow(t, 0.8));
    }

    private static double hash(int a, int b) {
        int h = a * 73856093 ^ b * 19349663;
        h ^= h >>> 13;
        h *= 0x5bd1e995;
        h ^= h >>> 15;
        return ((h >>> 8) & 0xFFFF) / 65535.0;
    }

    private void drawSea(Frame f) {
        double[] sun = sunPos();
        double[] moon = moonPos();
        double[] light = sun != null && f.weather() == Weather.SUNNY ? sun
                : (moon != null && f.weather() == Weather.SUNNY ? moon : null);
        int glint = sun != null ? Colors.mix(Colors.rgb(0xFFF1B8), Colors.rgb(0xFFB070), warmth) : Colors.rgb(0xDDE4FF);
        double lightStrength = sun != null ? 0.65 : 0.45 * nightness();
        for (int y = HORIZON; y < H; y++) {
            int base = seaColorAt(y, f.weather());
            double t = (y - HORIZON) / (double) (H - HORIZON);
            int seg = 2 + (int) (t * 11);
            double speed = (y % 2 == 0 ? 1 : -0.6) * (3 + t * 12);
            int off = (int) Math.floor(time * speed);
            int hi = Colors.mix(base, Colors.lighten(skyBottom, 0.2), 0.38);
            int lo = Colors.shade(base, 0.86);
            for (int x = 0; x < W; x++) {
                double hv = hash(Math.floorDiv(x + off, seg), y);
                int c = hv < 0.11 ? hi : (hv > 0.9 ? lo : base);
                if (light != null) {
                    double width = 4 + t * 20;
                    double d = Math.abs(x - light[0]);
                    if (d < width && hash(Math.floorDiv(x + (int) (time * 6), 2), y + (int) (time * 3) % 3) < 0.45 * (1 - d / width)) {
                        c = Colors.mix(c, glint, lightStrength * (1 - t * 0.5));
                    }
                }
                pc.px[y * W + x] = c;
            }
        }
        pc.hline(0, W - 1, HORIZON, Colors.mix(seaColorAt(HORIZON, f.weather()), skyBottom, 0.6));
    }

    // ===================================================================== pier

    private static final int WOOD = Colors.rgb(0xB98552);
    private static final int WOOD_DARK = Colors.rgb(0x7E4F2E);
    private static final int WOOD_SIDE = Colors.rgb(0x8A5634);
    private static final int WOOD_OUTLINE = Colors.rgb(0x4A2C1A);
    private static final int POST = Colors.rgb(0x6A4127);
    private static final double[] POSTS_Z = {1.12, 1.55, 2.1, 2.8, 3.6, 4.15};

    private void drawPier(Frame f) {
        int yFar = (int) Math.ceil(yAt(Z_FAR));

        // shadow the pier casts on the water to its right
        for (int y = yFar; y < H; y++) {
            double z = zAtY(y);
            int r = (int) Math.round(rightAt(z));
            int len = (int) Math.round(12 / z);
            for (int x = r; x < r + len; x++) {
                pc.set(x, y + (int) (DECK_THICK / z), Colors.withAlpha(0xFF08121E, 0.28 * (1 - (x - r) / (double) len)));
            }
        }

        // posts under the right edge (the side face covers their tops)
        int waterTint = seaColorAt(H - 20, f.weather());
        for (double z : POSTS_Z) {
            double px = rightAt(z) - 3 / z;
            double top = yAt(z) + DECK_THICK / z - 1;
            double len = POST_LEN / z;
            int wPost = Math.max(2, (int) Math.round(5 / z));
            double waterline = top + len * 0.55;
            for (int y = (int) top; y < (int) (top + len); y++) {
                for (int dx = 0; dx < wPost; dx++) {
                    int c = dx == 0 ? Colors.lighten(POST, 0.18) : (dx == wPost - 1 ? Colors.shade(POST, 0.7) : POST);
                    if (y >= waterline) {
                        c = Colors.mix(c, waterTint, Math.min(1, 0.35 + (y - waterline) / len));
                    }
                    pc.set((int) px + dx, y, c);
                }
            }
            int wob = (int) Math.round(Math.sin(time * 2 + z * 5));
            pc.hline((int) px - 1 + wob, (int) px + wPost + wob, (int) waterline, Colors.withAlpha(0xFFE8F2F6, 0.55));
        }

        // deck surface, one screen row at a time
        boolean snowy = f.weather() == Weather.SNOWY;
        for (int y = yFar; y < H; y++) {
            double z = zAtY(y);
            double zNext = zAtY(y + 1);
            int l = (int) Math.round(leftAt(z));
            int r = (int) Math.round(rightAt(z));
            double planks = (z - 1) * 7.5;
            int plank = (int) Math.floor(planks);
            double frac = planks - plank;
            double rowSpan = (z - zNext) * 7.5;
            boolean seam = frac < Math.max(0.06, rowSpan * 0.9);
            boolean plankFront = frac > 1 - rowSpan * 1.2;
            int plankColor = Colors.shade(WOOD, 0.9 + hash(plank, 7) * 0.16);
            double fade = Math.min(1, (z - 1) / 3.2);
            int haze = Colors.mix(skyBottom, WOOD_DARK, 0.6);
            for (int x = Math.max(0, l); x <= Math.min(W - 1, r); x++) {
                double u = (x - leftAt(z)) / (rightAt(z) - leftAt(z));
                int c;
                if (seam) {
                    c = WOOD_DARK;
                } else {
                    c = plankFront ? Colors.lighten(plankColor, 0.12) : plankColor;
                    double grain = hash(plank * 31 + (int) Math.floor(u * 14), (int) (frac * 6));
                    if (grain > 0.9) {
                        c = Colors.shade(c, 0.9);
                    } else if (grain < 0.05) {
                        c = Colors.lighten(c, 0.08);
                    }
                    boolean nail = Math.abs(u - 0.08) < 0.012 || Math.abs(u - 0.92) < 0.012;
                    if (nail && Math.abs(frac - 0.5) < 0.08 && z < 2.6) {
                        c = Colors.rgb(0x4A4448);
                    }
                }
                if (x == l) {
                    c = Colors.shade(c, 0.75);
                } else if (x == r) {
                    c = Colors.lighten(c, 0.18);
                }
                c = Colors.mix(c, haze, fade * 0.25);
                if (snowy && !seam && hash(x / 2, y) > 0.18) {
                    c = Colors.mix(c, Colors.rgb(0xF2F5FA), 0.82);
                }
                pc.set(x, y, c);
            }
            // side face below the right edge
            int faceH = (int) Math.round(DECK_THICK / z);
            for (int k = 1; k <= faceH; k++) {
                int c = k == faceH ? WOOD_OUTLINE : (k == 1 ? Colors.lighten(WOOD_SIDE, 0.1) : WOOD_SIDE);
                if (k > 1 && k < faceH && hash(plank, k) > 0.85) {
                    c = Colors.shade(WOOD_SIDE, 0.88);
                }
                pc.set(r, y + k, c);
                pc.set(r + 1, y + k, Colors.shade(c, 0.9));
            }
        }
        // far end face of the pier
        int fl = (int) Math.round(leftAt(Z_FAR));
        int fr = (int) Math.round(rightAt(Z_FAR));
        pc.hline(fl, fr, yFar, Colors.lighten(WOOD, 0.1));
        pc.hline(fl, fr, yFar + 1, WOOD_SIDE);
        pc.hline(fl, fr, yFar + 2, WOOD_OUTLINE);
    }

    // ===================================================================== objects on the pier

    private void drawObjects(Frame f) {
        bucketRects.clear();
        friendTags.clear();
        List<BucketView> list = f.buckets();
        for (int i = 0; i < list.size(); i++) {
            bucketRects.add(new int[]{0, 0, 0, 0});
        }
        // far to near so that closer things cover farther ones
        List<double[]> order = new ArrayList<>(); // z, kind, index
        for (int i = 0; i < list.size(); i++) {
            order.add(new double[]{bucketZ(i), 0, i});
        }
        order.add(new double[]{rabbitZ, 1, 0});
        order.add(new double[]{LANTERN_Z, 2, 0});
        order.add(new double[]{rabbitZ - HOLDER_BEHIND, 3, 0});
        for (int i = 0; i < friends.size(); i++) {
            FriendAnim fa = friendAnims.get(friends.get(i).id());
            if (fa != null && fa.z > 0) {
                order.add(new double[]{fa.z, 4, i});
            }
        }
        order.sort((a, b) -> Double.compare(b[0], a[0]));
        for (double[] o : order) {
            switch ((int) o[1]) {
                case 0 -> {
                    int i = (int) o[2];
                    BucketView b = list.get(i);
                    int shown = Math.min(shownFish.getOrDefault(b.subject().id(), b.fish().size()), b.fish().size());
                    bucketRects.set(i, drawBucket(i, Colors.parseHex(b.subject().colorHex(), Colors.rgb(0x6FA8DC)),
                            b.fish().subList(0, shown), i == f.activeIndex(), i == f.hoverIndex(), f.weather()));
                }
                case 1 -> drawRabbit(f);
                case 2 -> drawLantern(f);
                case 4 -> drawFriend((int) o[2]);
                default -> {
                    if (f.rabbit() != RabbitMode.FISHING || meWalking) {
                        drawRestingRod();
                    } else {
                        drawHolder();
                    }
                }
            }
        }
    }

    private void shadowEllipse(double cx, double cy, double rx, double ry, double alpha) {
        for (int y = (int) (cy - ry); y <= (int) (cy + ry); y++) {
            for (int x = (int) (cx - rx); x <= (int) (cx + rx); x++) {
                double dx = (x + 0.5 - cx) / rx;
                double dy = (y + 0.5 - cy) / ry;
                double d = dx * dx + dy * dy;
                if (d <= 1) {
                    pc.set(x, y, Colors.withAlpha(0xFF2A160C, alpha * (1 - d * 0.6)));
                }
            }
        }
    }

    private int[] drawBucket(int index, int color, List<FishType> fish, boolean active, boolean hover, Weather w) {
        double z = bucketZ(index);
        double s = bucketScale(index);
        int bw = Math.max(6, (int) Math.round(16 * s));
        int bh = Math.max(5, (int) Math.round(13 * s));
        int cx = (int) Math.round(xAt(BUCKET_U, z));
        int bottom = (int) Math.round(yAt(z));
        int bx = cx - bw / 2;
        int by = bottom - bh;

        shadowEllipse(cx + 2 * s, bottom, bw * 0.62, Math.max(1.5, 2.4 * s), 0.35);

        int outline = Colors.shade(color, 0.42);
        int dark = Colors.shade(color, 0.72);
        int light = Colors.lighten(color, 0.3);
        int metal = Colors.rgb(0x8A8A94);

        double hr = bw / 2.0 - 0.5;
        for (int a = 0; a <= 180; a += 5) {
            double rad = Math.toRadians(a);
            pc.set((int) Math.round(bx + bw / 2.0 - 0.5 + Math.cos(rad) * hr),
                    (int) Math.round(by + 1 - Math.sin(rad) * hr * 0.8), metal);
        }

        if (s >= 0.8) {
            int visible = Math.min(fish.size(), 9);
            for (int k = visible - 1; k >= 0; k--) {
                FishType ft = fish.get(fish.size() - 1 - k);
                int row = k / 3;
                int col = k % 3;
                int fx = bx + 1 + (int) Math.round(col * (bw - 7) / 2.0) - (row % 2 == 1 ? 1 : 0);
                int fy = by - row * 2;
                drawMiniFish(Math.max(bx, Math.min(bx + bw - 6, fx)), fy, ft, (k + row) % 2 == 0);
            }
        } else {
            for (int k = 0; k < Math.min(fish.size(), bw - 2); k++) {
                FishType ft = fish.get(fish.size() - 1 - k);
                pc.set(bx + 1 + k % (bw - 2), by - (k / (bw - 2)), ft.body());
            }
        }

        pc.hline(bx + 1, bx + bw - 2, by, Colors.shade(color, 0.3));
        pc.hline(bx, bx + bw - 1, by + 1, light);
        pc.set(bx, by, outline);
        pc.set(bx + bw - 1, by, outline);
        int inset = Math.max(1, (int) Math.round(2 * s));
        for (int r = 1; r < bh; r++) {
            int in = (r * inset) / (bh - 1);
            int left = bx + in;
            int right = bx + bw - 1 - in;
            for (int x = left; x <= right; x++) {
                int c;
                if (x == left || x == right || r == bh - 1) {
                    c = outline;
                } else if (r == 1) {
                    c = light;
                } else if (r == bh * 3 / 10 || r == bh * 7 / 10) {
                    c = Colors.mix(dark, metal, 0.35);
                } else if (x <= left + Math.max(1, bw / 7)) {
                    c = light;
                } else if (x >= right - Math.max(1, bw / 5)) {
                    c = dark;
                } else {
                    c = color;
                }
                pc.set(x, by + r, c);
            }
        }
        if (bw >= 10) {
            pc.set(bx + 2, by + bh / 2, Colors.lighten(color, 0.55));
            pc.set(bx + 2, by + bh / 2 + 1, Colors.lighten(color, 0.45));
        }
        if (w == Weather.SNOWY) {
            pc.hline(bx + 1, bx + bw / 3, by, Colors.rgb(0xF4F7FB));
            pc.hline(bx + bw * 2 / 3, bx + bw - 2, by, Colors.rgb(0xF4F7FB));
        }
        int handleTop = by - (int) Math.ceil(hr * 0.8);
        if (active || hover) {
            int ring = active ? Colors.rgb(0xFFF2C4) : Colors.withAlpha(0xFFFFF2C4, 0.6);
            int bounce = active ? (int) Math.round(Math.sin(time * 4)) : 0;
            int ax = bx + bw / 2;
            int ay = handleTop - 6 + bounce - (fish.size() > 3 ? 3 : 0);
            pc.hline(ax - 2, ax + 2, ay, ring);
            pc.hline(ax - 1, ax + 1, ay + 1, ring);
            pc.set(ax, ay + 2, ring);
        }
        int top = handleTop - 2;
        return new int[]{bx - 1, top, bw + 2, bottom - top + 2};
    }

    /** 6x3 fish: head with an eye, belly line and a forked tail. */
    private void drawMiniFish(int x, int y, FishType ft, boolean flip) {
        String[] rows = {".bbb.b", "beblb.", ".ooo.o"};
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 6; c++) {
                char ch = rows[r].charAt(c);
                if (ch == '.') {
                    continue;
                }
                int color = switch (ch) {
                    case 'e' -> Colors.rgb(0x1E1E28);
                    case 'l' -> ft.belly();
                    case 'o' -> Colors.shade(ft.body(), 0.5);
                    default -> ft.body();
                };
                pc.set(flip ? x + 5 - c : x + c, y - 1 + r, color);
            }
        }
        if (ft.rare()) {
            pc.set(flip ? x + 3 : x + 2, y - 1, Colors.lighten(ft.body(), 0.6));
        }
    }

    private int[] lanternGlass() {
        int x = (int) Math.round(xAt(LANTERN_U, LANTERN_Z));
        int base = (int) Math.round(yAt(LANTERN_Z));
        return new int[]{x, base - 26, base};
    }

    private void drawLantern(Frame f) {
        int[] g = lanternGlass();
        int x = g[0];
        int top = g[1];
        int base = g[2];
        int pole = Colors.rgb(0x3E2A1E);
        shadowEllipse(x + 2, base, 4, 1.5, 0.3);
        pc.vline(x, top + 6, base, pole);
        pc.vline(x + 1, top + 6, base, Colors.rgb(0x5A3D2A));
        pc.hline(x - 1, x + 2, base, pole);
        int iron = Colors.rgb(0x2E2A2A);
        pc.hline(x, x + 1, top - 2, iron);
        pc.hline(x - 1, x + 2, top - 1, iron);
        pc.vline(x - 1, top, top + 5, iron);
        pc.vline(x + 2, top, top + 5, iron);
        pc.hline(x - 1, x + 2, top + 5, iron);
        boolean lit = nightness() > 0.3 || f.weather() != Weather.SUNNY;
        pc.fillRect(x, top, 2, 5, lit ? Colors.rgb(0xFFD27A) : Colors.rgb(0xD8D2BC));
        if (f.weather() == Weather.SNOWY) {
            pc.hline(x - 1, x + 2, top - 2, Colors.rgb(0xF4F7FB));
        }
    }

    private int[] holderPos() {
        double hz = rabbitZ - HOLDER_BEHIND;
        return new int[]{(int) Math.round(rightAt(hz)) - 3, (int) Math.round(yAt(hz))};
    }

    private void drawHolder() {
        int[] h = holderPos();
        pc.fillRect(h[0] - 1, h[1] - 5, 3, 5, Colors.rgb(0x3E3A3A));
        pc.vline(h[0] - 1, h[1] - 5, h[1] - 1, Colors.rgb(0x6A6466));
    }

    /** Rod resting in its holder near the edge, line and bobber hooked to it. */
    private void drawRestingRod() {
        int[] h = holderPos();
        int tipX = h[0] + 42;
        int tipY = h[1] - 34;
        pc.line(h[0], h[1] - 2, tipX, tipY, Colors.rgb(0x5A3A22));
        pc.line(h[0] + 1, h[1] - 2, h[0] + 7, h[1] - 16, Colors.rgb(0x8A6040));
        pc.set(tipX, tipY, Colors.rgb(0xD9473A));
        int lineC = Colors.withAlpha(0xFFEDEDED, 0.7);
        for (int i = 1; i < 14; i++) {
            pc.set(tipX - i / 5, tipY + i, lineC);
        }
        pc.hline(tipX - 4, tipX - 2, tipY + 14, Colors.rgb(0xE04A3A));
        pc.hline(tipX - 4, tipX - 2, tipY + 15, Colors.rgb(0xF4EDE1));
        pc.fillCircle(h[0] + 3.5, h[1] - 8, 1.7, Colors.rgb(0x9A9AA6));
        pc.set(h[0] + 3, h[1] - 8, Colors.rgb(0x5A5A66));
        drawHolder();
    }

    // ===================================================================== rabbit

    private int bodyTop() {
        return rabbitBaseY() - Sprites.RABBIT_BODY.h + 1 - hop;
    }

    private int headY(RabbitMode m) {
        int y = bodyTop() - Sprites.RABBIT_HEAD.h + 3;
        if (m == RabbitMode.DOZING) {
            y += 1 + (Math.sin(time * 1.6) > 0.3 ? 1 : 0);
        }
        return y;
    }

    private double[] mugPos() {
        double cycle = time % 6.0;
        double lift = 0;
        if (cycle > 3.4 && cycle < 5.4) {
            lift = Math.min(1, Math.sin((cycle - 3.4) / 2.0 * Math.PI) * 1.6);
        }
        double restY = bodyTop() + 4;
        double sipY = headY(RabbitMode.COFFEE) + 8;
        return new double[]{rabbitBaseX() - 4, Math.round(restY + (sipY - restY) * lift), lift};
    }

    private double rodAngle(Frame f) {
        double fishing = 36;
        if (castT < 0.45) {
            return fishing + (118 - fishing) * Math.sin(castT / 0.45 * Math.PI / 2);
        }
        if (castT < 0.7) {
            return 118 + (28 - 118) * ((castT - 0.45) / 0.25);
        }
        if (castT < 0.95) {
            return 28 + (fishing - 28) * ((castT - 0.7) / 0.25);
        }
        if (reelT < 0.5) {
            return fishing + 16 * Math.sin(reelT / 0.5 * Math.PI);
        }
        if (f.rabbit() == RabbitMode.FISHING && f.secondsToNextCatch() <= 3) {
            return fishing - 3 + Math.sin(time * 25) * 1.2;
        }
        return fishing + Math.sin(time * 0.9) * 0.8;
    }

    private double[] bobberPos(Frame f) {
        double y = BOBBER_Y + Math.sin(time * 2.1) * 0.7;
        if (f.rabbit() == RabbitMode.FISHING && f.secondsToNextCatch() <= 3 && flight == null) {
            y += 1.5 + Math.sin(time * 18) * 0.8;
        }
        return new double[]{BOBBER_X, y};
    }

    private void drawRabbit(Frame f) {
        RabbitMode mode = f.rabbit();
        if (meWalking) {
            mode = RabbitMode.PAUSED; // walking pose: paws down, eyes open, no rod or mug
        }
        int cx = rabbitBaseX();
        int bx = cx - Sprites.RABBIT_BODY.w / 2;
        int bt = bodyTop();
        int hx = cx - Sprites.RABBIT_HEAD.w / 2;
        int hy = headY(mode);

        shadowEllipse(cx + 3, rabbitBaseY() + 1, 12, 2.6, 0.38);

        // fluffy tail peeking out on the left
        pc.fillCircle(bx + 1, bt + 8, 3.2, Sprites.OUTLINE);
        pc.fillCircle(bx + 1, bt + 8, 2.3, Sprites.FUR);

        pc.sprite(Sprites.RABBIT_BODY, bx, bt, false);

        double[] mug = mode == RabbitMode.COFFEE ? mugPos() : null;
        boolean sipping = mug != null && mug[2] > 0.6;
        boolean closed = mode == RabbitMode.DOZING || sipping || blinkTimer < 0;
        pc.sprite(closed ? Sprites.RABBIT_HEAD_CLOSED : Sprites.RABBIT_HEAD, hx, hy, false);

        int droop = mode == RabbitMode.DOZING ? 1 : 0;
        int sway = (int) Math.round(Math.sin(time * 1.3) * 0.6);
        pc.sprite(Sprites.RABBIT_EAR, hx - 3, hy + 3 + droop + sway, false);
        pc.sprite(Sprites.RABBIT_EAR, hx + Sprites.RABBIT_HEAD.w - 2, hy + 3 + droop - sway, true);

        int hatY = hy - 3 + (mode == RabbitMode.DOZING ? 2 : 0);
        pc.sprite(Sprites.HAT, hx - 2, hatY, false);
        if (f.weather() == Weather.SNOWY) {
            int snowC = Colors.rgb(0xF4F7FB);
            pc.hline(hx + 5, hx + 12, hatY, snowC);
            pc.hline(hx + 6, hx + 10, hatY - 1, snowC);
            pc.set(hx - 1, hatY + 6, snowC);
            pc.set(hx + 18, hatY + 6, snowC);
        }

        if (mode == RabbitMode.FISHING) {
            drawRodAndLine(f);
        } else if (mode == RabbitMode.COFFEE) {
            int mx = (int) mug[0];
            int my = (int) mug[1];
            pc.sprite(Sprites.MUG, mx, my, false);
            drawPaw(mx - 1, my + 3);
            drawPaw(mx + 6, my + 3);
        } else {
            drawPaw(cx - 4, bt + 6);
            drawPaw(cx + 3, bt + 6);
        }
    }

    private void drawPaw(int x, int y) {
        pc.fillRect(x - 1, y - 1, 4, 3, Sprites.OUTLINE);
        pc.fillRect(x, y - 1, 2, 2, Sprites.FUR);
        pc.set(x, y, Sprites.FUR_SHADE);
    }

    private void drawRodAndLine(Frame f) {
        double a = Math.toRadians(rodAngle(f));
        double bx = rabbitBaseX() + 6;
        double by = bodyTop() + 8;
        double len = 56;
        double[] tip = {bx + Math.cos(a) * len, by - Math.sin(a) * len};
        pc.line((int) bx, (int) by, (int) tip[0], (int) tip[1], Colors.rgb(0x5A3A22));
        pc.line((int) bx, (int) by - 1, (int) (bx + Math.cos(a) * 22), (int) (by - Math.sin(a) * 22) - 1, Colors.rgb(0x8A6040));
        pc.set((int) tip[0], (int) tip[1], Colors.rgb(0xD9473A));
        double rx = bx + Math.cos(a) * 5;
        double ry = by - Math.sin(a) * 5 + 2;
        pc.fillCircle(rx, ry, 1.8, Colors.rgb(0x9A9AA6));
        pc.set((int) rx, (int) ry, Colors.rgb(0x55555F));
        drawPaw((int) Math.round(bx + Math.cos(a) * 7) - 1, (int) Math.round(by - Math.sin(a) * 7));

        int lineC = Colors.withAlpha(0xFFEDEDED, 0.75);
        double[] end;
        boolean showBobber = true;
        if (castT < 0.6) {
            end = null;
        } else if (castT < 1.2) {
            double t = (castT - 0.6) / 0.6;
            double[] target = bobberPos(f);
            end = new double[]{tip[0] + (target[0] - tip[0]) * t, tip[1] + (target[1] - tip[1]) * t - Math.sin(t * Math.PI) * 20};
        } else if (flight != null) {
            end = new double[]{flight.x + 5, flight.y + 1};
            showBobber = false;
        } else {
            end = bobberPos(f);
        }
        if (end == null) {
            return;
        }
        double sag = flight != null ? 0 : (castT < 1.2 ? 2 : 10);
        double mx = (tip[0] + end[0]) / 2;
        double my = Math.max(tip[1], end[1]) + sag - Math.abs(end[1] - tip[1]) * 0.25;
        int prevX = (int) tip[0];
        int prevY = (int) tip[1];
        for (int i = 1; i <= 60; i++) {
            double t = i / 60.0;
            int ix = (int) Math.round((1 - t) * (1 - t) * tip[0] + 2 * (1 - t) * t * mx + t * t * end[0]);
            int iy = (int) Math.round((1 - t) * (1 - t) * tip[1] + 2 * (1 - t) * t * my + t * t * end[1]);
            if (ix != prevX || iy != prevY) {
                pc.set(ix, iy, lineC);
                prevX = ix;
                prevY = iy;
            }
        }
        if (showBobber) {
            int x = (int) Math.round(end[0]);
            int y = (int) Math.round(end[1]);
            int red = Colors.rgb(0xE04A3A);
            pc.set(x, y - 3, Colors.rgb(0x3A2A22));
            pc.hline(x - 1, x + 1, y - 2, red);
            pc.hline(x - 1, x + 1, y - 1, red);
            pc.hline(x - 1, x + 1, y, Colors.rgb(0xF4EDE1));
            pc.set(x - 1, y - 2, Colors.lighten(red, 0.35));
            if (castT >= 1.2 && f.rabbit() == RabbitMode.FISHING && f.secondsToNextCatch() <= 3 && rnd.nextDouble() < 0.08) {
                ripples.add(new double[]{x, y + 1, 0, 5});
            }
        }
    }

    private void drawFlight() {
        if (flight == null) {
            return;
        }
        Sprite s = Sprites.FISH.get(flight.type);
        int wiggle = Math.sin(flight.t * 40) > 0 ? 1 : 0;
        if (flight.type.big()) {
            pc.sprite2x(s, (int) flight.x - 5, (int) flight.y - 4 + wiggle, false);
        } else {
            pc.sprite(s, (int) flight.x, (int) flight.y + wiggle, false);
        }
        if (flight.type.rare()) {
            for (int i = 0; i < 2; i++) {
                sparkles.add(new double[]{flight.x + rnd.nextDouble() * 10, flight.y + rnd.nextDouble() * 5, 0.3, 1});
            }
        }
    }

    // ===================================================================== friends on the pier

    /** A friend in the study room, as the scene draws them. */
    public record FriendView(String id, String name, int hatColor, RabbitMode mode) {
    }

    private static final double FRIEND_U = 0.93;
    public static final int MAX_FRIENDS_ON_PIER = 3;

    /**
     * Depth of seat {@code k} when {@code count} rabbits share the pier: they split its length
     * (from the near edge at z = 1 to the far end) into count + 1 equal parts.
     */
    private static double seatZ(int k, int count) {
        return 1 + (Z_FAR - 1) * (k + 1.0) / (count + 1);
    }

    private static double walkToward(double z, double target, double dt) {
        double step = WALK_SPEED * dt;
        if (Math.abs(target - z) <= step) {
            return target;
        }
        return z + Math.signum(target - z) * step;
    }

    private static final class FriendAnim {
        RabbitMode last;
        double castT = 99;
        double reelT = 99;
        double blink = 2;
        double phase;
        FishType fish;
        double fishT = 99;
        double z = -1;
        boolean walking;
    }

    private List<FriendView> friends = List.of();
    /** Friends keep the order they joined in; a newcomer takes the seat at the far end. */
    private final List<String> seatOrder = new ArrayList<>();
    private final Map<String, FriendAnim> friendAnims = new HashMap<>();
    private final Map<Integer, Sprite> hatCache = new HashMap<>();
    private final List<double[]> friendTags = new ArrayList<>(); // x, y, friend index

    /** Who sits on the pier with you, in the order they joined. Only the first three fit. */
    public void setFriends(List<FriendView> list) {
        Map<String, FriendView> byId = new HashMap<>();
        for (FriendView v : list) {
            byId.put(v.id(), v);
        }
        seatOrder.removeIf(id -> !byId.containsKey(id));
        List<FriendView> newcomers = new ArrayList<>();
        for (FriendView v : list) {
            if (!seatOrder.contains(v.id())) {
                newcomers.add(v);
            }
        }
        newcomers.sort(java.util.Comparator.comparing(FriendView::id));
        for (FriendView v : newcomers) {
            seatOrder.add(v.id());
        }
        List<FriendView> ordered = new ArrayList<>();
        for (String id : seatOrder) {
            ordered.add(byId.get(id));
        }
        friends = new ArrayList<>(ordered.subList(0, Math.min(MAX_FRIENDS_ON_PIER, ordered.size())));
        java.util.Set<String> ids = new java.util.HashSet<>();
        for (FriendView v : friends) {
            ids.add(v.id());
        }
        friendAnims.keySet().retainAll(ids);
    }

    /** A friend caught a fish: their rod jerks and the fish jumps out of the water to them. */
    public void friendCatch(String id, FishType type) {
        for (int i = 0; i < friends.size(); i++) {
            if (friends.get(i).id().equals(id)) {
                FriendAnim a = friendAnims.get(id);
                if (a == null) {
                    return;
                }
                a.reelT = 0;
                a.fish = type;
                a.fishT = 0;
                double[] b = friendBobber(i, a);
                for (int k = 0; k < 7; k++) {
                    drops.add(new double[]{b[0], b[1], (rnd.nextDouble() - 0.5) * 35, -25 - rnd.nextDouble() * 35, 0.2});
                }
                ripples.add(new double[]{b[0], b[1] + 1, 0, 7});
                return;
            }
        }
    }

    private void updateFriends(double dt) {
        int count = friends.size() + 1;
        for (int i = 0; i < friends.size(); i++) {
            FriendView v = friends.get(i);
            FriendAnim a = friendAnims.computeIfAbsent(v.id(), k -> {
                FriendAnim n = new FriendAnim();
                n.phase = rnd.nextDouble() * 6;
                n.last = v.mode();
                n.castT = v.mode() == RabbitMode.FISHING ? 99 : 0;
                return n;
            });
            if (a.last != v.mode()) {
                if (v.mode() == RabbitMode.FISHING) {
                    a.castT = 0;
                }
                a.last = v.mode();
            }
            double target = seatZ(i + 1, count);
            if (a.z < 0) {
                a.z = target; // a newcomer sits down straight at the far seat
            }
            boolean wasWalking = a.walking;
            a.z = walkToward(a.z, target, dt);
            a.walking = a.z != target;
            if (wasWalking && !a.walking && v.mode() == RabbitMode.FISHING) {
                a.castT = 0;
            }
            a.castT += dt;
            a.reelT += dt;
            a.fishT += dt;
            a.blink -= dt;
            if (a.blink < -0.15) {
                a.blink = 2.5 + rnd.nextDouble() * 3;
            }
        }
    }

    /** Friends are drawn relative to the usual size of your rabbit, smaller the further they sit. */
    private static double friendScale(FriendAnim a) {
        return Math.min(1, RABBIT_Z / a.z);
    }

    private double[] friendBobber(int i, FriendAnim a) {
        double z = a.z;
        double s = friendScale(a);
        return new double[]{rightAt(z) + 46 * s, yAt(z) + 12 * s + Math.sin(time * 2.1 + a.phase) * 0.5};
    }

    private Sprite hatFor(int color) {
        return hatCache.computeIfAbsent(color, c -> {
            Map<Character, Integer> p = new HashMap<>();
            p.put('k', Colors.shade(c, 0.35));
            p.put('g', c);
            p.put('G', Colors.lighten(c, 0.22));
            p.put('D', Colors.shade(c, 0.78));
            p.put('b', Colors.shade(c, 0.58));
            p.put('r', Colors.rgb(0xD9473A));
            p.put('f', Colors.rgb(0xF2C14E));
            return new Sprite(new String[]{
                    "......" + "kkkkkkkkkk" + "......",
                    "....." + "k" + "GGgggggggg" + "k" + ".....",
                    "...." + "k" + "GGgggggggggD" + "k" + "....",
                    "...." + "k" + "bbbbbbbrfbbb" + "k" + "....",
                    "...." + "k" + "gggggggggggD" + "k" + "....",
                    ".." + "kk" + "GggggggggggggD" + "kk" + "..",
                    "k" + "GGggggggggggggggggDD" + "k",
                    "." + "kkkkkkkkkkkkkkkkkkkk" + ".",
            }, p);
        });
    }

    private static void paw(PixelCanvas c, int x, int y) {
        c.fillRect(x - 1, y - 1, 4, 3, Sprites.OUTLINE);
        c.fillRect(x, y - 1, 2, 2, Sprites.FUR);
        c.set(x, y, Sprites.FUR_SHADE);
    }

    /** Draws a friend's rabbit at full size into a small canvas (30x36, feet at the bottom centre). */
    private PixelCanvas composeFriend(FriendView v, FriendAnim a) {
        PixelCanvas c = new PixelCanvas(30, 36);
        c.clear(0);
        RabbitMode mode = a.walking ? RabbitMode.PAUSED : v.mode();
        double lift = 0;
        if (mode == RabbitMode.COFFEE) {
            double cycle = (time + a.phase) % 6.0;
            if (cycle > 3.4 && cycle < 5.4) {
                lift = Math.min(1, Math.sin((cycle - 3.4) / 2.0 * Math.PI) * 1.6);
            }
        }
        boolean closed = mode == RabbitMode.DOZING || lift > 0.6 || a.blink < 0;
        int doze = mode == RabbitMode.DOZING ? 1 : 0;
        c.fillCircle(7, 32, 3.2, Sprites.OUTLINE);
        c.fillCircle(7, 32, 2.3, Sprites.FUR);
        c.sprite(Sprites.RABBIT_BODY, 6, 24, false);
        c.sprite(closed ? Sprites.RABBIT_HEAD_CLOSED : Sprites.RABBIT_HEAD, 6, 13 + doze, false);
        int sway = (int) Math.round(Math.sin(time * 1.3 + a.phase) * 0.6);
        c.sprite(Sprites.RABBIT_EAR, 3, 16 + doze + sway, false);
        c.sprite(Sprites.RABBIT_EAR, 22, 16 + doze - sway, true);
        c.sprite(hatFor(v.hatColor()), 4, 10 + doze * 2, false);
        if (mode == RabbitMode.COFFEE) {
            int my = (int) Math.round(28 + (21 - 28) * lift);
            c.sprite(Sprites.MUG, 11, my, false);
            paw(c, 10, my + 3);
            paw(c, 17, my + 3);
        } else if (mode == RabbitMode.FISHING) {
            paw(c, 20, 30);
        } else {
            paw(c, 11, 30);
            paw(c, 18, 30);
        }
        return c;
    }

    /** Shrinks a sprite canvas with 4x4 supersampling and draws it; keeps edges crisp. */
    private void blitScaled(PixelCanvas src, int dx, int dy, int tw, int th) {
        double sx = src.w / (double) tw;
        double sy = src.h / (double) th;
        for (int ty = 0; ty < th; ty++) {
            for (int tx = 0; tx < tw; tx++) {
                int n = 0;
                int hits = 0;
                int r = 0;
                int g = 0;
                int b = 0;
                boolean outline = false;
                for (int j = 0; j < 4; j++) {
                    for (int k = 0; k < 4; k++) {
                        int px = (int) ((tx + (k + 0.5) / 4) * sx);
                        int py = (int) ((ty + (j + 0.5) / 4) * sy);
                        n++;
                        int c = src.get(px, py);
                        if ((c >>> 24) != 0) {
                            hits++;
                            r += (c >> 16) & 0xFF;
                            g += (c >> 8) & 0xFF;
                            b += c & 0xFF;
                            if (c == Sprites.OUTLINE) {
                                outline = true;
                            }
                        }
                    }
                }
                if (hits * 2 >= n) {
                    int c = 0xFF000000 | ((r / hits) << 16) | ((g / hits) << 8) | (b / hits);
                    if (outline && hits < n) {
                        c = Colors.mix(c, Sprites.OUTLINE, 0.55);
                    }
                    pc.set(dx + tx, dy + ty, c);
                }
            }
        }
    }

    private void drawFriend(int i) {
        if (i >= friends.size()) {
            return;
        }
        FriendView v = friends.get(i);
        FriendAnim a = friendAnims.get(v.id());
        if (a == null) {
            return;
        }
        double z = a.z;
        double s = friendScale(a);
        int x = (int) Math.round(xAt(FRIEND_U, z));
        int base = (int) Math.round(yAt(z));
        int tw = Math.max(6, (int) Math.round(30 * s));
        int th = Math.max(7, (int) Math.round(36 * s));
        int left = x - tw / 2;
        int fhop = a.walking ? (int) Math.round(Math.abs(Math.sin(time * 11 + a.phase)) * 2 * s) : 0;
        int top = base - th + 1 - fhop;
        shadowEllipse(x + 2 * s, base + 1, 12 * s, Math.max(1.2, 2.6 * s), 0.38);
        blitScaled(composeFriend(v, a), left, top, tw, th);
        // name tag to the right of the head, out over the water, so tags never stack
        friendTags.add(new double[]{left + tw - 2 * s, top + 9 * s, i});

        if (v.mode() == RabbitMode.FISHING && !a.walking) {
            double angle = 36;
            if (a.castT < 0.45) {
                angle = 36 + 82 * Math.sin(a.castT / 0.45 * Math.PI / 2);
            } else if (a.castT < 0.7) {
                angle = 118 - 90 * ((a.castT - 0.45) / 0.25);
            } else if (a.castT < 0.95) {
                angle = 28 + 8 * ((a.castT - 0.7) / 0.25);
            } else if (a.reelT < 0.5) {
                angle = 36 + 16 * Math.sin(a.reelT / 0.5 * Math.PI);
            } else {
                angle = 36 + Math.sin(time * 0.9 + a.phase) * 0.8;
            }
            double rad = Math.toRadians(angle);
            double bx = left + 20 * s;
            double by = top + 30 * s;
            double len = 52 * s;
            double[] tip = {bx + Math.cos(rad) * len, by - Math.sin(rad) * len};
            pc.line((int) bx, (int) by, (int) tip[0], (int) tip[1], Colors.rgb(0x5A3A22));
            pc.set((int) tip[0], (int) tip[1], Colors.rgb(0xD9473A));
            if (a.castT >= 0.6) {
                double[] bob = friendBobber(i, a);
                double[] end = bob;
                if (a.castT < 1.2) {
                    double t = (a.castT - 0.6) / 0.6;
                    end = new double[]{tip[0] + (bob[0] - tip[0]) * t, tip[1] + (bob[1] - tip[1]) * t - Math.sin(t * Math.PI) * 12};
                }
                int lineC = Colors.withAlpha(0xFFEDEDED, 0.65);
                double mx = (tip[0] + end[0]) / 2;
                double my = Math.max(tip[1], end[1]) + 6 * s;
                for (int k = 1; k <= 30; k++) {
                    double t = k / 30.0;
                    pc.set((int) Math.round((1 - t) * (1 - t) * tip[0] + 2 * (1 - t) * t * mx + t * t * end[0]),
                            (int) Math.round((1 - t) * (1 - t) * tip[1] + 2 * (1 - t) * t * my + t * t * end[1]), lineC);
                }
                if (a.fishT > 0.9) {
                    int ex = (int) Math.round(end[0]);
                    int ey = (int) Math.round(end[1]);
                    pc.hline(ex - 1, ex, ey - 1, Colors.rgb(0xE04A3A));
                    pc.hline(ex - 1, ex, ey, Colors.rgb(0xF4EDE1));
                }
            }
        }

        // the caught fish jumps from the water up to the friend
        if (a.fish != null && a.fishT < 0.9) {
            double t = a.fishT / 0.9;
            double[] from = friendBobber(i, a);
            double fx = from[0] + (x - from[0]) * t;
            double fy = from[1] + (top + 6 * s - from[1]) * t - Math.sin(t * Math.PI) * 26 * s;
            drawMiniFish((int) fx - 3, (int) fy, a.fish, true);
            if (a.fish.rare()) {
                sparkles.add(new double[]{fx + rnd.nextDouble() * 6 - 3, fy, 0.4, 1});
            }
        }
    }

    // ===================================================================== grading & lights

    private void grade(Weather w) {
        double n = nightness();
        double r = 1 - n * 0.58 + warmth * 0.06;
        double g = 1 - n * 0.52 - warmth * 0.03;
        double b = 1 - n * 0.28 - warmth * 0.1;
        double desat = 0;
        if (w == Weather.RAINY) {
            r *= 0.86;
            g *= 0.88;
            b *= 0.92;
            desat = 0.3;
        } else if (w == Weather.SNOWY) {
            r *= 0.96;
            g *= 0.98;
            b *= 1.02;
            desat = 0.12;
        }
        for (int i = 0; i < pc.px.length; i++) {
            if (pc.mask[i] == 0) {
                continue;
            }
            int c = pc.px[i];
            int cr = (c >> 16) & 0xFF;
            int cg = (c >> 8) & 0xFF;
            int cb = c & 0xFF;
            if (desat > 0) {
                int lum = (cr * 3 + cg * 6 + cb) / 10;
                cr += (int) ((lum - cr) * desat);
                cg += (int) ((lum - cg) * desat);
                cb += (int) ((lum - cb) * desat);
            }
            pc.px[i] = 0xFF000000 | (Colors.clamp((int) (cr * r)) << 16)
                    | (Colors.clamp((int) (cg * g)) << 8) | Colors.clamp((int) (cb * b));
        }
    }

    private void drawLights(Frame f) {
        double n = nightness();
        boolean lit = n > 0.3 || f.weather() != Weather.SUNNY;
        int warm = Colors.rgb(0xFFC56A);
        if (lit) {
            int[] g = lanternGlass();
            double s = Math.max(0.25, n) * (0.92 + 0.08 * Math.sin(time * 7) * Math.sin(time * 3.1));
            pc.glow(g[0] + 1, g[1] + 2.5, 30, warm, 0.3 * s);
            pc.glow(g[0] + 1, g[1] + 2.5, 7, Colors.rgb(0xFFE7B0), 0.45 * s);
            for (int y = g[2] - 6; y < g[2] + 14; y++) {
                for (int x = g[0] - 26; x < g[0] + 30; x++) {
                    if (onDeck(x, y)) {
                        double d = Math.hypot((x - g[0]) / 26.0, (y - g[2] - 2) / 9.0);
                        if (d < 1) {
                            pc.add(x, y, warm, 0.22 * s * (1 - d));
                        }
                    }
                }
            }
            pc.fillRect(g[0], g[1], 2, 5, Colors.rgb(0xFFD983));
        }
        if (n > 0.35) {
            int cx = 262;
            int cy = nearRidge[cx + 6] - 6;
            pc.fillRect(cx + 3, cy + 2, 2, 2, Colors.rgb(0xFFD27A));
            pc.fillRect(cx + 7, cy + 2, 2, 2, Colors.rgb(0xFFD27A));
            pc.glow(cx + 6, cy + 3, 6, warm, 0.3 * n);
        }
        if (n > 0.5 && f.weather() == Weather.SUNNY) {
            double a = (n - 0.5) * 2;
            for (double[] ff : fireflies) {
                double blink = Math.max(0, Math.sin(time * 1.8 + ff[4]));
                if (blink > 0.05) {
                    pc.glow(ff[0], ff[1], 4, Colors.rgb(0xD8FF7A), 0.5 * blink * a);
                    pc.add((int) ff[0], (int) ff[1], Colors.rgb(0xF2FFB0), blink * a);
                }
            }
        }
    }

    private void drawParticles() {
        double l = skyLight;
        int rainC = Colors.mix(Colors.rgb(0x5E6E86), Colors.rgb(0xCFDBE8), l);
        for (double[] d : rain) {
            int x = (int) d[0];
            int y = (int) d[1];
            for (int i = 0; i < (int) d[4]; i++) {
                pc.set(x + i / 3, y - i, Colors.withAlpha(rainC, 0.55 - i * 0.08));
            }
        }
        int snowC = Colors.mix(Colors.rgb(0x9AA6BE), Colors.rgb(0xFFFFFF), l);
        for (double[] s : snow) {
            int x = (int) s[0];
            int y = (int) s[1];
            pc.set(x, y, Colors.withAlpha(snowC, 0.95));
            if (s[5] > 1) {
                pc.set(x + 1, y, Colors.withAlpha(snowC, 0.8));
                pc.set(x, y + 1, Colors.withAlpha(snowC, 0.8));
                pc.set(x + 1, y + 1, Colors.withAlpha(snowC, 0.6));
            }
        }
        int rip = Colors.mix(Colors.rgb(0x8FA4B8), Colors.rgb(0xE6F0F6), l);
        for (double[] r : ripples) {
            double t = r[2] / 0.7;
            double rad = 1 + t * r[3];
            int alphaC = Colors.withAlpha(rip, 0.6 * (1 - t));
            for (int a = 0; a < 360; a += 20) {
                double rr = Math.toRadians(a);
                pc.set((int) Math.round(r[0] + Math.cos(rr) * rad), (int) Math.round(r[1] + Math.sin(rr) * rad * 0.35), alphaC);
            }
        }
        for (double[] d : drops) {
            pc.set((int) d[0], (int) d[1], Colors.withAlpha(rip, 0.85));
        }
        for (double[] s : steam) {
            pc.set((int) s[0], (int) s[1], Colors.withAlpha(0xFFF4F1EA, 0.5 * (1 - s[2] / 2.2)));
        }
        for (double[] s : sparkles) {
            if (s[2] < 0) {
                continue;
            }
            double t = s[2] / 0.8;
            int c = s[3] > 0 ? Colors.rgb(0xFFE27A) : Colors.rgb(0xFFFFFF);
            int x = (int) s[0];
            int y = (int) (s[1] - t * 4);
            pc.set(x, y, Colors.withAlpha(c, 1 - t));
            if (t < 0.5) {
                pc.set(x - 1, y, Colors.withAlpha(c, 0.5 - t));
                pc.set(x + 1, y, Colors.withAlpha(c, 0.5 - t));
                pc.set(x, y - 1, Colors.withAlpha(c, 0.5 - t));
                pc.set(x, y + 1, Colors.withAlpha(c, 0.5 - t));
            }
        }
    }

    // ===================================================================== overlay (text)

    /** A floating message near the top of the scene, e.g. when a friend catches a fish. */
    public void toast(String text, boolean rare) {
        popups.add(new Popup(text, W / 2.0 + 10, 78 + (popups.size() % 3) * 11, rare, new double[]{0}, true));
    }

    /** Screen rectangle (scene pixels: x, y, w, h) of a bucket from the last frame, or null. */
    public int[] bucketRect(int i) {
        return i >= 0 && i < bucketRects.size() ? bucketRects.get(i).clone() : null;
    }

    /** Index of the bucket under a point in scene pixels, or -1. Nearer buckets win. */
    public int bucketAt(double vx, double vy) {
        for (int i = 0; i < bucketRects.size(); i++) {
            int[] r = bucketRects.get(i);
            if (vx >= r[0] && vx <= r[0] + r[2] && vy >= r[1] && vy <= r[1] + r[3]) {
                return i;
            }
        }
        return -1;
    }

    public void drawOverlay(GraphicsContext gc, double scale, double ox, double oy, Frame f, boolean paused) {
        gc.setTextBaseline(VPos.CENTER);
        gc.setTextAlign(TextAlignment.CENTER);

        Font tagFont = Font.font("Avenir", FontWeight.BOLD, Math.max(9, scale * 2.4));
        friendTags.sort((p, q) -> Double.compare(p[2], q[2]));
        double lastY = Double.MAX_VALUE;
        for (double[] tag : friendTags) {
            int i = (int) tag[2];
            if (i >= friends.size()) {
                continue;
            }
            FriendView v = friends.get(i);
            double tw = textWidth(v.name(), tagFont) + scale * 6;
            double cx = ox + tag[0] * scale + tw / 2;
            // nearer friends get the lower tags; push farther ones up so tags never overlap
            double cy = Math.min(oy + tag[1] * scale, lastY - scale * 6.4);
            lastY = cy;
            gc.setFill(Color.rgb(52, 34, 24, 0.78));
            gc.fillRoundRect(cx - tw / 2, cy - scale * 2.6, tw, scale * 5.2, scale * 2.6, scale * 2.6);
            int hc = v.hatColor();
            gc.setFill(Color.rgb((hc >> 16) & 0xFF, (hc >> 8) & 0xFF, hc & 0xFF));
            gc.fillOval(cx - tw / 2 + scale * 1.4, cy - scale * 0.9, scale * 1.8, scale * 1.8);
            gc.setFill(Color.rgb(255, 244, 222));
            gc.setFont(tagFont);
            gc.fillText(v.name(), cx + scale * 1.2, cy);
        }

        int labelIndex = f.hoverIndex();
        if (labelIndex >= 0 && labelIndex < f.buckets().size() && labelIndex < bucketRects.size()) {
            BucketView b = f.buckets().get(labelIndex);
            int[] r = bucketRects.get(labelIndex);
            Font font = Font.font("Avenir", FontWeight.BOLD, Math.max(11, scale * 3.2));
            String text = b.subject().name() + "  ·  " + b.fish().size() + " fish";
            double tw = textWidth(text, font) + scale * 8;
            double cx = ox + (r[0] + r[2] / 2.0) * scale;
            double cy = oy + (r[1] - 6) * scale;
            gc.setFill(Color.rgb(52, 34, 24, 0.86));
            gc.fillRoundRect(cx - tw / 2, cy - scale * 3.8, tw, scale * 7.6, scale * 3, scale * 3);
            gc.setFill(Color.web(b.subject().colorHex()));
            gc.fillOval(cx - tw / 2 + scale * 2, cy - scale, scale * 2, scale * 2);
            gc.setFill(Color.rgb(255, 244, 222));
            gc.setFont(font);
            gc.fillText(text, cx + scale, cy);
        }

        if (f.buckets().isEmpty()) {
            Font hint = Font.font("Avenir", FontWeight.BOLD, Math.max(12, scale * 3.6));
            String text = "Add a bucket for the subject you'll study";
            double tw = textWidth(text, hint) + scale * 8;
            double cx = ox + 80 * scale;
            double cy = oy + 196 * scale;
            gc.setFont(hint);
            gc.setFill(Color.rgb(52, 34, 24, 0.8));
            gc.fillRoundRect(cx - tw / 2, cy - scale * 5, tw, scale * 10, scale * 4, scale * 4);
            gc.setFill(Color.rgb(255, 240, 214));
            gc.fillText(text, cx, cy);
        }

        for (double[] z : zzz) {
            double t = z[2] / 2.6;
            gc.setFont(Font.font("Avenir", FontWeight.BOLD, Math.max(9, scale * 3) * (0.7 + t * 0.7)));
            gc.setFill(Color.rgb(255, 246, 225, Math.max(0, 1 - t) * 0.9));
            gc.fillText("z", ox + z[0] * scale, oy + z[1] * scale);
        }

        if (paused) {
            Font pf = Font.font("Avenir", FontWeight.BOLD, Math.max(10, scale * 3));
            gc.setFont(pf);
            double cx = ox + rabbitBaseX() * scale;
            double cy = oy + (headY(RabbitMode.COFFEE) - 9) * scale;
            double tw = textWidth("paused", pf) + scale * 5;
            gc.setFill(Color.rgb(52, 34, 24, 0.8));
            gc.fillRoundRect(cx - tw / 2, cy - scale * 3.5, tw, scale * 7, scale * 3, scale * 3);
            gc.setFill(Color.rgb(255, 230, 170));
            gc.fillText("paused", cx, cy);
        }

        for (Popup p : popups) {
            double t = p.age()[0] / (p.toast() ? 4.0 : (p.rare() ? 2.6 : 1.6));
            Font pf = Font.font("Avenir", FontWeight.BOLD, Math.max(10, scale * (p.rare() ? 3.6 : 3)));
            gc.setFont(pf);
            double alpha = t < 0.75 ? 1 : Math.max(0, (1 - t) / 0.25);
            double x = ox + p.x() * scale;
            double y = oy + (p.y() - t * 10) * scale;
            if (p.toast()) {
                y = oy + (p.y() - t * 4) * scale;
                double tw = textWidth(p.text(), pf) + scale * 8;
                gc.setFill(Color.rgb(52, 34, 24, 0.82 * alpha));
                gc.fillRoundRect(x - tw / 2, y - scale * 4, tw, scale * 8, scale * 4, scale * 4);
            }
            gc.setFill(Color.rgb(40, 24, 16, 0.6 * alpha));
            gc.fillText(p.text(), x + scale * 0.5, y + scale * 0.5);
            gc.setFill(p.rare() ? Color.rgb(255, 214, 90, alpha) : Color.rgb(255, 248, 230, alpha));
            gc.fillText(p.text(), x, y);
        }
    }

    private static Text measure;

    private static double textWidth(String s, Font font) {
        if (measure == null) {
            measure = new Text();
        }
        measure.setFont(font);
        measure.setText(s);
        return measure.getLayoutBounds().getWidth();
    }
}
