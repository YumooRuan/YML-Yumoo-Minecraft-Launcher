package dev.yml.core.config;

import dev.yml.core.util.FileUtil;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Reads and rewrites control bindings inside a Minecraft {@code options.txt}.
 *
 * <p>Bindings are stored as {@code key_key.forward:key.keyboard.w}. Only the {@code key_*}
 * entries are touched, every other option is preserved byte for byte (line order included).</p>
 */
public final class Keybinds {

    /** Known vanilla actions, in the order shown by the game. */
    public static final Map<String, String> ACTION_NAMES = new LinkedHashMap<>();
    /** Bind order used when a bind is missing from options.txt. */
    public static final List<String> ACTION_ORDER = new ArrayList<>();

    static {
        add("key.forward", "前进");
        add("key.back", "后退");
        add("key.left", "向左移动");
        add("key.right", "向右移动");
        add("key.jump", "跳跃");
        add("key.sneak", "潜行");
        add("key.sprint", "疾跑");
        add("key.attack", "攻击 / 破坏");
        add("key.use", "使用 / 放置");
        add("key.pickItem", "选取方块");
        add("key.drop", "丢弃物品");
        add("key.inventory", "打开背包");
        add("key.swapOffhand", "交换副手");
        add("key.chat", "打开聊天");
        add("key.playerlist", "玩家列表");
        add("key.command", "输入命令");
        add("key.socialInteractions", "社交屏幕");
        add("key.screenshot", "截图");
        add("key.togglePerspective", "切换视角");
        add("key.fullscreen", "切换全屏");
        add("key.advancements", "进度");
        add("key.spectatorOutlines", "观察者高亮");
        add("key.smoothCamera", "平滑视角");
        add("key.saveToolbarActivator", "保存快捷栏");
        add("key.loadToolbarActivator", "加载快捷栏");
        for (int i = 1; i <= 9; i++) {
            add("key.hotbar." + i, "快捷栏 " + i);
        }
        add("key.zoom", "缩放视角");
    }

    private static void add(String action, String label) {
        ACTION_NAMES.put(action, label);
        ACTION_ORDER.add(action);
    }

    private Keybinds() {
    }

    public static String label(String action) {
        return ACTION_NAMES.getOrDefault(action, action.startsWith("key_key.") ? action.substring(8) : action
                .replace("key.", ""));
    }

    /** Returns all bindings found in the file, in file order. */
    public static LinkedHashMap<String, String> read(Path optionsFile) {
        LinkedHashMap<String, String> binds = new LinkedHashMap<>();
        if (!Files.isRegularFile(optionsFile)) {
            return binds;
        }
        for (String line : FileUtil.readString(optionsFile).split("\\R")) {
            int idx = line.indexOf(':');
            if (idx <= 0) {
                continue;
            }
            String key = line.substring(0, idx).trim();
            if (key.startsWith("key_")) {
                binds.put(key.substring(4), line.substring(idx + 1).trim());
            }
        }
        return binds;
    }

    /** Full ordered list: known actions first (with their binds), then unknown ones from the file. */
    public static LinkedHashMap<String, String> fullList(Path optionsFile) {
        LinkedHashMap<String, String> existing = read(optionsFile);
        LinkedHashMap<String, String> ordered = new LinkedHashMap<>();
        for (String action : ACTION_ORDER) {
            ordered.put(action, existing.getOrDefault(action, ""));
        }
        for (var entry : existing.entrySet()) {
            ordered.putIfAbsent(entry.getKey(), entry.getValue());
        }
        return ordered;
    }

    /**
     * Rewrites the {@code key_*} lines of the file, preserving everything else.
     * Missing actions are appended at the end.
     */
    public static void apply(Path optionsFile, Map<String, String> binds) {
        Set<String> written = new LinkedHashSet<>();
        List<String> lines = new ArrayList<>();
        boolean existed = Files.isRegularFile(optionsFile);
        if (existed) {
            for (String line : FileUtil.readString(optionsFile).split("\\R", -1)) {
                int idx = line.indexOf(':');
                if (idx > 0) {
                    String key = line.substring(0, idx).trim();
                    if (key.startsWith("key_") && binds.containsKey(key.substring(4))) {
                        String action = key.substring(4);
                        lines.add("key_" + action + ":" + binds.get(action));
                        written.add(action);
                        continue;
                    }
                }
                lines.add(line);
            }
        }
        for (var entry : binds.entrySet()) {
            if (!written.contains(entry.getKey()) && entry.getValue() != null && !entry.getValue().isBlank()) {
                lines.add("key_" + entry.getKey() + ":" + entry.getValue());
            }
        }
        String content = String.join("\n", lines);
        if (!content.endsWith("\n")) {
            content = content + "\n";
        }
        FileUtil.writeString(optionsFile, content);
    }

    /** Copies the bindings of one options.txt onto another instance. */
    public static int copyBinds(Path from, Path to) {
        LinkedHashMap<String, String> binds = read(from);
        if (binds.isEmpty()) {
            return 0;
        }
        apply(to, binds);
        return binds.size();
    }

    // ------------------------------------------------------------ rendering

    private static final Map<String, String> SPECIAL = Map.ofEntries(
            Map.entry("escape", "Esc"),
            Map.entry("enter", "Enter"),
            Map.entry("tab", "Tab"),
            Map.entry("space", "空格"),
            Map.entry("backspace", "退格"),
            Map.entry("delete", "Delete"),
            Map.entry("insert", "Insert"),
            Map.entry("home", "Home"),
            Map.entry("end", "End"),
            Map.entry("page.up", "PageUp"),
            Map.entry("page.down", "PageDown"),
            Map.entry("up", "↑"),
            Map.entry("down", "↓"),
            Map.entry("left", "←"),
            Map.entry("right", "→"),
            Map.entry("caps.lock", "CapsLock"),
            Map.entry("left.shift", "左 Shift"),
            Map.entry("right.shift", "右 Shift"),
            Map.entry("left.control", "左 Ctrl"),
            Map.entry("right.control", "右 Ctrl"),
            Map.entry("left.alt", "左 Alt"),
            Map.entry("right.alt", "右 Alt"),
            Map.entry("left.win", "左 Win"),
            Map.entry("right.win", "右 Win"),
            Map.entry("menu", "菜单键"),
            Map.entry("print.screen", "PrintScreen"),
            Map.entry("scroll.lock", "ScrollLock"),
            Map.entry("pause", "Pause"),
            Map.entry("num.lock", "NumLock"),
            Map.entry("keypad.enter", "小键盘 Enter"),
            Map.entry("keypad.add", "小键盘 +"),
            Map.entry("keypad.subtract", "小键盘 -"),
            Map.entry("keypad.multiply", "小键盘 *"),
            Map.entry("keypad.divide", "小键盘 /"),
            Map.entry("keypad.decimal", "小键盘 ."),
            Map.entry("unknown", "未绑定"));

    private static final Map<String, String> MOUSE = Map.of(
            "left", "鼠标左键",
            "right", "鼠标右键",
            "middle", "鼠标中键",
            "button.3", "鼠标侧键 1",
            "button.4", "鼠标侧键 2");

    public static String display(String glfwName) {
        if (glfwName == null || glfwName.isBlank()) {
            return "未绑定";
        }
        String name = glfwName.trim().toLowerCase(Locale.ROOT);
        if (name.startsWith("key.mouse.")) {
            return MOUSE.getOrDefault(name.substring("key.mouse.".length()), "鼠标按键");
        }
        String key = name.startsWith("key.keyboard.") ? name.substring("key.keyboard.".length()) : name;
        if (SPECIAL.containsKey(key)) {
            return SPECIAL.get(key);
        }
        if (key.startsWith("f") && key.length() <= 3 && key.substring(1).chars().allMatch(Character::isDigit)) {
            return key.toUpperCase(Locale.ROOT);
        }
        if (key.length() == 1) {
            return key.toUpperCase(Locale.ROOT);
        }
        return key;
    }

    /** Reads the global preset kept by the launcher. */
    public static LinkedHashMap<String, String> loadPreset(Path presetFile) {
        LinkedHashMap<String, String> binds = new LinkedHashMap<>();
        if (!Files.isRegularFile(presetFile)) {
            return binds;
        }
        try {
            var map = dev.yml.core.json.Json.obj(dev.yml.core.json.Json.parse(presetFile));
            for (var entry : map.entrySet()) {
                if (entry.getValue() instanceof String s && !s.isBlank()) {
                    binds.put(entry.getKey(), s);
                }
            }
        } catch (Exception ignored) {
        }
        return binds;
    }

    public static void savePreset(Path presetFile, Map<String, String> binds) {
        var map = new LinkedHashMap<String, Object>(binds);
        FileUtil.writeString(presetFile,
                dev.yml.core.json.Json.write(map, true));
    }

    public static byte[] utf8(String s) {
        return s.getBytes(StandardCharsets.UTF_8);
    }
}
