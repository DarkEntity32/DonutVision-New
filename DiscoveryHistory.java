package dev.donutvision;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.world.phys.AABB;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Per-server, per-dimension history of block observations and heuristic base markers.
 * IMPORTANT: these are old client observations, NOT live server truth or Anti-Xray bypass.
 * Only called from the client thread. Saved periodically and on disconnect.
 */
public final class DiscoveryHistory {
    public record KnownBlock(BlockPos pos, int originalColor) { }
    public record KnownBase(int x, int y, int z, int score, int storages, int utilities,
                            int devices, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        public BlockPos pos() { return new BlockPos(x, y, z); }
        public AABB region() { return new AABB(minX, minY, minZ, maxX, maxY, maxZ); }
    }

    private static final int BLOCK_LIMIT = 16000;
    private static final int BASE_LIMIT = 512;
    private static final long SAVE_INTERVAL_MS = 10_000L;
    private static final Map<Long, KnownBlock> blocks = new LinkedHashMap<>();
    private static final Map<Long, KnownBase> bases = new LinkedHashMap<>();
    private static ClientLevel activeLevel;
    private static String activeKey;
    private static Path activePath;
    private static boolean dirty;
    private static long lastSave;

    private DiscoveryHistory() { }

    /** Changes memory namespace when switching server, dimension, or world. */
    public static void tick(Minecraft mc) {
        if (mc.level == null) {
            if (activeLevel != null) {
                flush();
                reset();
            }
            return;
        }
        String server = mc.getCurrentServer() != null ? mc.getCurrentServer().ip : "local-world";
        String key = server + "|" + mc.level.dimension().location();
        if (mc.level != activeLevel || !key.equals(activeKey)) {
            flush();
            reset();
            activeLevel = mc.level;
            activeKey = key;
            activePath = FabricLoader.getInstance().getConfigDir()
                    .resolve("donutvision").resolve("discoveries")
                    .resolve(sha256(key) + ".txt");
            load();
        }
        if (dirty && System.currentTimeMillis() - lastSave >= SAVE_INTERVAL_MS) flush();
    }

    private static String sha256(String input) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256").digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(bytes, 0, 16);
        } catch (Exception e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public static void rememberBlock(BlockPos position, int originalColor) {
        if (activeLevel == null || originalColor == 0) return;
        long key = position.asLong();
        KnownBlock old = blocks.get(key);
        if (old != null && old.originalColor() == originalColor) return;
        blocks.put(key, new KnownBlock(position.immutable(), originalColor));
        trim(blocks, BLOCK_LIMIT);
        dirty = true;
    }

    public static void rememberBase(BaseFinder.Site site) {
        if (activeLevel == null || site.historical()) return;
        long key = chunkKey(site.pos().getX() >> 4, site.pos().getZ() >> 4);
        KnownBase old = bases.get(key);
        if (old != null && old.score() >= site.score()) return;
        AABB r = site.region();
        bases.put(key, new KnownBase(site.pos().getX(), site.pos().getY(), site.pos().getZ(),
                site.score(), site.storages(), site.utilities(), site.devices(),
                (int)r.minX, (int)r.minY, (int)r.minZ,
                (int)r.maxX, (int)r.maxY, (int)r.maxZ));
        trim(bases, BASE_LIMIT);
        dirty = true;
    }

    static long chunkKey(int x, int z) { return ((long)x << 32) | (z & 0xFFFFFFFFL); }

    public static Collection<KnownBlock> blocks() { return blocks.values(); }
    public static Collection<KnownBase> bases() { return bases.values(); }
    public static int blockCount() { return blocks.size(); }
    public static int baseCount() { return bases.size(); }

    private static <K, V> void trim(Map<K, V> map, int limit) {
        while (map.size() > limit) map.remove(map.keySet().iterator().next());
    }

    private static void reset() {
        activeLevel = null;
        activeKey = null;
        activePath = null;
        blocks.clear();
        bases.clear();
        dirty = false;
        lastSave = 0;
    }

    private static void load() {
        if (activePath == null || !Files.isRegularFile(activePath)) return;
        try (BufferedReader in = Files.newBufferedReader(activePath, StandardCharsets.UTF_8)) {
            String line;
            int read = 0;
            while ((line = in.readLine()) != null && read++ < BLOCK_LIMIT + BASE_LIMIT + 1000) {
                try {
                    String[] p = line.split("\\t");
                    if (p[0].equals("B") && p.length == 5) {
                        int x = Integer.parseInt(p[1]), y = Integer.parseInt(p[2]);
                        int z = Integer.parseInt(p[3]), color = Integer.parseInt(p[4]);
                        BlockPos pos = new BlockPos(x, y, z);
                        blocks.put(pos.asLong(), new KnownBlock(pos, color));
                    } else if (p[0].equals("S") && p.length == 14) {
                        int[] a = new int[13];
                        for (int i = 0; i < 13; i++) a[i] = Integer.parseInt(p[i+1]);
                        KnownBase base = new KnownBase(a[0],a[1],a[2],a[3],a[4],a[5],a[6],
                                a[7],a[8],a[9],a[10],a[11],a[12]);
                        bases.put(chunkKey(base.x() >> 4, base.z() >> 4), base);
                    }
                } catch (RuntimeException ignored) { /* Skip malformed lines. */ }
            }
            trim(blocks, BLOCK_LIMIT);
            trim(bases, BASE_LIMIT);
        } catch (IOException e) {
            System.err.println("[DonutVision] Could not read discoveries: " + e.getMessage());
        }
    }

    private static void flush() {
        if (!dirty || activePath == null) return;
        Path temp = activePath.resolveSibling(activePath.getFileName().toString() + ".tmp");
        try {
            Files.createDirectories(activePath.getParent());
            try (BufferedWriter out = Files.newBufferedWriter(temp, StandardCharsets.UTF_8)) {
                out.write("# DonutVision old observations, not proof of current world state");
                out.newLine();
                for (KnownBlock block : blocks.values()) {
                    BlockPos p = block.pos();
                    out.write("B\t" + p.getX() + "\t" + p.getY() + "\t" + p.getZ()
                            + "\t" + block.originalColor());
                    out.newLine();
                }
                for (KnownBase b : bases.values()) {
                    out.write("S\t" + b.x() + "\t" + b.y() + "\t" + b.z() + "\t"
                            + b.score() + "\t" + b.storages() + "\t" + b.utilities()
                            + "\t" + b.devices() + "\t" + b.minX() + "\t"
                            + b.minY() + "\t" + b.minZ() + "\t" + b.maxX()
                            + "\t" + b.maxY() + "\t" + b.maxZ());
                    out.newLine();
                }
            }
            try {
                Files.move(temp, activePath, StandardCopyOption.REPLACE_EXISTING,
                        StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(temp, activePath, StandardCopyOption.REPLACE_EXISTING);
            }
            dirty = false;
            lastSave = System.currentTimeMillis();
        } catch (IOException e) {
            System.err.println("[DonutVision] Could not save discoveries: " + e.getMessage());
        }
    }
}
