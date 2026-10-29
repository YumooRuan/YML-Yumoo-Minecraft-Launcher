package dev.yml.ui.page;

import dev.yml.core.config.InstanceSettings;
import dev.yml.core.content.ModUpdate;
import dev.yml.core.content.ModrinthClient;
import dev.yml.core.game.LoaderType;
import dev.yml.core.game.LocalVersions;
import dev.yml.core.net.Downloader;
import dev.yml.core.util.FileUtil;
import dev.yml.core.util.Progress;
import dev.yml.ui.AppContext;
import dev.yml.ui.ModUpdateDialog;
import dev.yml.ui.Page;
import dev.yml.ui.Ui;
import dev.yml.ui.UiHost;
import dev.yml.ui.Util;
import dev.yml.ui.component.Icon;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Local mod management for the selected instance. */
public final class ModsPage implements Page {

    private final AppContext context;
    private final UiHost host;
    private final BorderPane view = new BorderPane();
    private final ComboBox<String> instanceBox = new ComboBox<>();
    private final VBox list = new VBox(4);
    private final Label pathLabel = Ui.label("", "tiny", "faint");
    private final Label summary = Ui.label("", "tiny", "faint");
    private final List<LocalVersions.Installed> instances = new ArrayList<>();
    private LocalVersions.Installed current;

    public ModsPage(AppContext context, UiHost host) {
        this.context = context;
        this.host = host;
        build();
    }

    private void build() {
        view.setPadding(new Insets(18, 20, 14, 20));
        HBox header = Ui.row(10, Ui.h1("模组管理"), Ui.small("启用 / 禁用通过重命名实现，可直接拖入 jar 文件"));
        header.setAlignment(Pos.CENTER_LEFT);

        instanceBox.setPrefWidth(300);
        instanceBox.setOnAction(event -> selectInstance());
        Button open = new Button("打开模组文件夹");
        open.getStyleClass().addAll("btn", "btn-small");
        open.setOnAction(event -> Util.openFolder(modsDir()));
        Button add = new Button("添加模组");
        add.getStyleClass().addAll("btn", "btn-small");
        add.setOnAction(event -> addMods());
        Button check = new Button("检查更新");
        check.getStyleClass().addAll("btn", "btn-small");
        check.setOnAction(event -> checkUpdates());
        HBox toolbar = new HBox(10, Ui.label("实例", "small", "muted"), instanceBox, Ui.spacer(), check, open, add);
        toolbar.setAlignment(Pos.CENTER_LEFT);

        ScrollPane scroll = new ScrollPane(list);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox top = new VBox(10, header, toolbar, pathLabel);
        view.setTop(top);
        view.setCenter(scroll);
        view.setBottom(summary);
        BorderPane.setMargin(summary, new Insets(8, 0, 0, 2));
    }

    @Override
    public Node view() {
        return view;
    }

    @Override
    public void onShow() {
        refreshInstances();
    }

    private void refreshInstances() {
        instances.clear();
        instances.addAll(context.versions());
        List<String> names = new ArrayList<>();
        for (LocalVersions.Installed installed : instances) {
            names.add(installed.id());
        }
        instanceBox.getItems().setAll(names);
        LocalVersions.Installed selected = context.selected();
        int index = 0;
        if (selected != null) {
            for (int i = 0; i < instances.size(); i++) {
                if (instances.get(i).id().equals(selected.id())) {
                    index = i;
                    break;
                }
            }
        }
        if (!names.isEmpty()) {
            instanceBox.getSelectionModel().select(index);
            selectInstance();
        } else {
            list.getChildren().setAll(Ui.small("还没有安装任何版本"));
        }
    }

    private void selectInstance() {
        int index = instanceBox.getSelectionModel().getSelectedIndex();
        if (index < 0 || index >= instances.size()) {
            return;
        }
        current = instances.get(index);
        context.select(current);
        InstanceSettings settings = context.instanceSettings(current);
        pathLabel.setText("目录: " + modsDir());
        if (current.loader() == LoaderType.VANILLA) {
            summary.setText("当前是原版实例，模组需要先安装 Fabric / Forge / NeoForge 加载器");
        } else {
            summary.setText("");
        }
        rebuild();
    }

    private Path modsDir() {
        if (current == null) {
            return context.settings.mcPath().resolve("mods");
        }
        InstanceSettings settings = context.instanceSettings(current);
        return current.modsDir(context.settings.mcPath(), settings.isolated);
    }

    private record ModFile(Path file, dev.yml.core.game.ModScanner.Meta meta, boolean enabled,
                           byte[] icon, dev.yml.core.game.ModScanner.Relations relations) {
    }

    private record Conflict(String modId, String modName, String type, String severity,
                            String detail, String suggestion) {
    }

    private int scanVersion;

    private void rebuild() {
        list.getChildren().clear();
        Path dir = modsDir();
        if (!Files.isDirectory(dir)) {
            VBox empty = new VBox(8, Ui.small("这个实例还没有模组文件夹"),
                    Ui.small("点击“添加模组”或把 jar 拖进文件夹"));
            empty.setPadding(new Insets(20, 8, 8, 8));
            list.getChildren().add(empty);
            return;
        }
        int request = ++scanVersion;
        list.getChildren().add(Ui.small("正在扫描模组..."));
        context.io.submit(() -> {
            List<ModFile> mods = scanMods(dir);
            javafx.application.Platform.runLater(() -> {
                if (request != scanVersion) {
                    return;
                }
                renderMods(mods);
            });
        });
    }

    private List<ModFile> scanMods(Path dir) {
        List<Path> files;
        try (var stream = Files.list(dir)) {
            files = stream.filter(Files::isRegularFile).sorted().toList();
        } catch (Exception e) {
            return List.of();
        }
        List<ModFile> mods = new ArrayList<>();
        for (Path file : files) {
            String lower = file.getFileName().toString().toLowerCase(Locale.ROOT);
            if (!lower.endsWith(".jar") && !lower.endsWith(".jar.disabled")) {
                continue;
            }
            dev.yml.core.game.ModScanner.Meta meta =
                    dev.yml.core.game.ModScanner.meta(file);
            // PCL2-like: only jars with real mod metadata are listed, libraries are ignored.
            if (meta == null) {
                continue;
            }
            mods.add(new ModFile(file, meta, lower.endsWith(".jar"),
                    dev.yml.core.game.ModScanner.iconBytes(file),
                    dev.yml.core.game.ModScanner.relations(file)));
        }
        return mods;
    }

    private void renderMods(List<ModFile> mods) {
        list.getChildren().clear();
        if (mods.isEmpty()) {
            list.getChildren().add(Ui.small("没有识别到模组（非模组 jar 不会显示）"));
            return;
        }
        java.util.Map<String, List<ModFile>> groups = new java.util.LinkedHashMap<>();
        for (ModFile mod : mods) {
            groups.computeIfAbsent(mod.meta().id().toLowerCase(Locale.ROOT), k -> new ArrayList<>())
                    .add(mod);
        }
        List<Conflict> conflicts = analyzeConflicts(mods, groups);
        if (!conflicts.isEmpty()) {
            list.getChildren().add(conflictBanner(conflicts));
        }
        for (List<ModFile> group : groups.values()) {
            group.sort(java.util.Comparator
                    .comparing((ModFile m) -> !m.enabled())
                    .thenComparing(m -> m.file().getFileName().toString().toLowerCase(Locale.ROOT)));
            renderGroup(group, conflicts);
        }
        summary.setText(groups.size() + " 个模组 · " + mods.size() + " 个文件（已按模组 ID 合并）"
                + (conflicts.isEmpty() ? " · 未发现冲突" : " · " + conflicts.size() + " 个冲突"));
    }

    private List<Conflict> analyzeConflicts(List<ModFile> mods,
                                            java.util.Map<String, List<ModFile>> groups) {
        List<Conflict> conflicts = new ArrayList<>();
        LoaderType instance = current == null ? LoaderType.UNKNOWN : current.loader();

        for (var entry : groups.entrySet()) {
            List<ModFile> group = entry.getValue();
            if (group.size() > 1) {
                String versions = group.stream()
                        .map(m -> m.meta().version().isBlank() ? m.file().getFileName().toString()
                                : m.meta().version())
                        .distinct().collect(java.util.stream.Collectors.joining(" / "));
                conflicts.add(new Conflict(entry.getKey(), group.get(0).meta().name(),
                        "新旧版本冲突", "严重",
                        "同一模组存在 " + group.size() + " 个文件：" + versions,
                        "只保留一个与当前游戏版本匹配的文件，其余移除或禁用"));
            }
        }

        for (var entry : groups.entrySet()) {
            ModFile mod = entry.getValue().get(0);
            LoaderType modLoader = parseLoader(mod.meta().loader());
            if (!loaderCompatible(modLoader, instance)) {
                conflicts.add(new Conflict(entry.getKey(), mod.meta().name(),
                        "加载器不匹配", "严重",
                        mod.meta().name() + " 是 " + modLoader.label() + " 模组，当前实例是 "
                                + instance.label(),
                        "安装与实例加载器一致的版本，或改用对应加载器的实例"));
            }
        }

        java.util.Map<String, ModFile> installed = new java.util.LinkedHashMap<>();
        for (ModFile mod : mods) {
            installed.putIfAbsent(mod.meta().id().toLowerCase(Locale.ROOT), mod);
            for (String provided : mod.relations().provides()) {
                installed.putIfAbsent(provided.toLowerCase(Locale.ROOT), mod);
            }
        }

        java.util.Set<String> reported = new java.util.HashSet<>();
        for (ModFile mod : mods) {
            String self = mod.meta().id().toLowerCase(Locale.ROOT);
            for (var incompatible : mod.relations().breaks().entrySet()) {
                String targetId = incompatible.getKey().toLowerCase(Locale.ROOT);
                ModFile target = installed.get(targetId);
                if (target == null || target == mod) {
                    continue;
                }
                if (!rangeMatches(target.meta().version(), incompatible.getValue())) {
                    continue;
                }
                String pair = self.compareTo(target.meta().id().toLowerCase(Locale.ROOT)) < 0
                        ? self + "|" + targetId : targetId + "|" + self;
                if (!reported.add(pair)) {
                    continue;
                }
                conflicts.add(new Conflict(self, mod.meta().name(),
                        "模组不兼容", "严重",
                        mod.meta().name() + " 声明与 " + target.meta().name() + " "
                                + target.meta().version() + " 不兼容（范围 "
                                + incompatible.getValue() + "）",
                        "禁用或删除其中一个模组"));
            }
        }

        for (ModFile mod : mods) {
            String self = mod.meta().id().toLowerCase(Locale.ROOT);
            for (var dependency : mod.relations().depends().entrySet()) {
                String depId = dependency.getKey().toLowerCase(Locale.ROOT);
                if (isVanillaDependency(depId) || installed.containsKey(depId)) {
                    continue;
                }
                conflicts.add(new Conflict(self, mod.meta().name(),
                        "缺少前置", "警告",
                        mod.meta().name() + " 需要前置 " + dependency.getKey()
                                + "（" + dependency.getValue() + "）",
                        "安装对应前置模组，或换用不需要该前置的版本"));
            }
        }
        return conflicts;
    }

    private static LoaderType parseLoader(String loader) {
        if (loader == null) {
            return LoaderType.UNKNOWN;
        }
        try {
            return LoaderType.valueOf(loader);
        } catch (IllegalArgumentException e) {
            return LoaderType.UNKNOWN;
        }
    }

    private static boolean loaderCompatible(LoaderType mod, LoaderType instance) {
        if (mod == LoaderType.UNKNOWN || instance == LoaderType.UNKNOWN) {
            return true;
        }
        return switch (instance) {
            case FABRIC -> mod == LoaderType.FABRIC;
            case QUILT -> mod == LoaderType.FABRIC || mod == LoaderType.QUILT;
            case FORGE -> mod == LoaderType.FORGE;
            case NEOFORGE -> mod == LoaderType.NEOFORGE;
            default -> false;
        };
    }

    private static boolean isVanillaDependency(String id) {
        return id.equals("minecraft") || id.equals("forge") || id.equals("neoforge")
                || id.equals("fabricloader") || id.equals("fabric-loader")
                || id.equals("quilt_loader") || id.equals("java");
    }

    private static boolean rangeMatches(String version, String range) {
        if (range == null || range.isBlank() || range.equals("*")) {
            return true;
        }
        String value = range.trim();
        if (value.startsWith("[") || value.startsWith("(")) {
            boolean lowerInclusive = value.startsWith("[");
            boolean upperInclusive = value.endsWith("]");
            String body = value.substring(1, value.length() - 1);
            String[] parts = body.split(",", -1);
            if (parts.length == 2) {
                String lower = parts[0].trim();
                String upper = parts[1].trim();
                if (!lower.isEmpty()) {
                    int cmp = compareVersions(version, lower);
                    if (cmp < 0 || (cmp == 0 && !lowerInclusive)) {
                        return false;
                    }
                }
                if (!upper.isEmpty()) {
                    int cmp = compareVersions(version, upper);
                    if (cmp > 0 || (cmp == 0 && !upperInclusive)) {
                        return false;
                    }
                }
                return true;
            }
        }
        if (value.contains(" ") && !value.startsWith("^") && !value.startsWith("~")) {
            for (String token : value.split("\\s+")) {
                if (!token.isBlank() && !comparatorMatches(version, token)) {
                    return false;
                }
            }
            return true;
        }
        return comparatorMatches(version, value);
    }

    private static boolean comparatorMatches(String version, String token) {
        String value = token.trim();
        if (value.isEmpty() || value.equals("*")) {
            return true;
        }
        try {
            if (value.startsWith(">=")) {
                return compareVersions(version, value.substring(2)) >= 0;
            }
            if (value.startsWith("<=")) {
                return compareVersions(version, value.substring(2)) <= 0;
            }
            if (value.startsWith(">")) {
                return compareVersions(version, value.substring(1)) > 0;
            }
            if (value.startsWith("<")) {
                return compareVersions(version, value.substring(1)) < 0;
            }
            if (value.startsWith("=")) {
                value = value.substring(1);
            }
            if (value.startsWith("^")) {
                String base = value.substring(1);
                return compareVersions(version, base) >= 0
                        && major(version) == major(base);
            }
            if (value.startsWith("~")) {
                String base = value.substring(1);
                return compareVersions(version, base) >= 0
                        && major(version) == major(base)
                        && minor(version) == minor(base);
            }
            return compareVersions(version, value) == 0;
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static int compareVersions(String a, String b) {
        String[] left = a.split("[.\\-_+]");
        String[] right = b.split("[.\\-_+]");
        for (int i = 0; i < Math.max(left.length, right.length); i++) {
            String x = i < left.length ? left[i] : "0";
            String y = i < right.length ? right[i] : "0";
            try {
                int cmp = Long.compare(Long.parseLong(x), Long.parseLong(y));
                if (cmp != 0) {
                    return cmp;
                }
            } catch (NumberFormatException e) {
                int cmp = x.compareToIgnoreCase(y);
                if (cmp != 0) {
                    return cmp;
                }
            }
        }
        return 0;
    }

    private static int major(String version) {
        try {
            return Integer.parseInt(version.split("[.\\-_+]")[0]);
        } catch (Exception e) {
            return -1;
        }
    }

    private static int minor(String version) {
        try {
            return Integer.parseInt(version.split("[.\\-_+]")[1]);
        } catch (Exception e) {
            return -1;
        }
    }

    private Node conflictBanner(List<Conflict> conflicts) {
        VBox banner = new VBox(6);
        banner.setPadding(new Insets(10, 12, 10, 12));
        banner.setStyle("-fx-background-color: rgba(255, 98, 89, 0.10);"
                + "-fx-border-color: rgba(255, 98, 89, 0.45); -fx-border-radius: 8;"
                + "-fx-background-radius: 8;");
        String types = conflicts.stream().map(Conflict::type).distinct()
                .collect(java.util.stream.Collectors.joining(" / "));
        Label title = Ui.label("发现 " + conflicts.size() + " 个模组冲突", "small");
        title.setStyle("-fx-text-fill: #ff8a83; -fx-font-weight: bold;");
        Label detail = Ui.small(types);
        detail.setWrapText(true);
        Button inspect = new Button("查看冲突");
        inspect.getStyleClass().addAll("btn", "btn-small");
        inspect.setOnAction(event -> showConflicts(conflicts));
        HBox row = new HBox(10, title, Ui.spacer(), inspect);
        row.setAlignment(Pos.CENTER_LEFT);
        banner.getChildren().addAll(row, detail);
        return banner;
    }

    private void showConflicts(List<Conflict> conflicts) {
        dev.yml.ui.component.DialogShell shell = new dev.yml.ui.component.DialogShell(
                host.window(), "模组冲突", 760, 520);
        VBox box = new VBox(8);
        for (Conflict conflict : conflicts) {
            VBox card = new VBox(4);
            card.setPadding(new Insets(10, 12, 10, 12));
            card.getStyleClass().add("card");
            HBox head = new HBox(8,
                    Ui.chip(conflict.severity(), "chip-red"),
                    Ui.label(conflict.type(), "small"),
                    Ui.label(conflict.modName(), "small", "muted"));
            head.setAlignment(Pos.CENTER_LEFT);
            Label detail = Ui.label(conflict.detail(), "small");
            detail.setWrapText(true);
            Label suggestion = Ui.label("建议：" + conflict.suggestion(), "tiny", "faint");
            suggestion.setWrapText(true);
            card.getChildren().addAll(head, detail, suggestion);
            box.getChildren().add(card);
        }
        ScrollPane scroll = new ScrollPane(box);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        shell.setContent(scroll);
        Button close = new Button("关闭");
        close.getStyleClass().add("btn");
        close.setOnAction(event -> shell.close());
        shell.setFooter(close);
        shell.showAndWait();
    }

    private void renderGroup(List<ModFile> group, List<Conflict> conflicts) {
        ModFile primary = group.get(0);
        boolean enabled = primary.enabled();
        Path file = primary.file();
        HBox row = new HBox(10);
        row.setPadding(new Insets(8, 10, 8, 10));
        row.getStyleClass().add("row-hover");
        row.setAlignment(Pos.CENTER_LEFT);

        String version = primary.meta().version();
        Label title = Ui.label(primary.meta().name() + (version.isBlank() ? "" : "  " + version), "small");
        HBox.setHgrow(title, Priority.ALWAYS);
        Label state = Ui.chip(enabled ? "已启用" : "已禁用", enabled ? "chip-green" : "chip-red");
        Button toggle = new Button(enabled ? "禁用" : "启用");
        toggle.getStyleClass().addAll("btn", "btn-small");
        toggle.setOnAction(event -> {
            toggle(file, enabled);
            rebuild();
        });
        Button delete = new Button();
        delete.setGraphic(Icon.of(Icon.TRASH, 13, Ui.RED));
        delete.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
        delete.setOnAction(event -> {
            delete(file);
            rebuild();
        });
        row.getChildren().addAll(modIcon(primary.icon()), title, state);
        List<Conflict> own = conflicts.stream()
                .filter(c -> c.modId().equals(primary.meta().id().toLowerCase(Locale.ROOT)))
                .toList();
        if (!own.isEmpty()) {
            Label chip = Ui.chip("冲突 " + own.size(), "chip-red");
            Ui.tooltip(chip, own.get(0).type() + "：" + own.get(0).detail());
            row.getChildren().add(chip);
        }
        if (group.size() > 1) {
            Button versions = new Button("共 " + group.size() + " 个文件");
            versions.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            versions.setOnAction(event -> showVersions(group));
            row.getChildren().add(versions);
        }
        row.getChildren().addAll(toggle, delete);
        list.getChildren().add(row);
    }

    private void showVersions(List<ModFile> group) {
        dev.yml.ui.component.DialogShell shell = new dev.yml.ui.component.DialogShell(
                host.window(), "模组文件 · " + group.get(0).meta().name(), 620, 420);
        VBox rows = new VBox(6);
        for (ModFile mod : group) {
            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setPadding(new Insets(6, 8, 6, 8));
            row.getStyleClass().add("row-hover");
            String name = mod.file().getFileName().toString();
            Label label = Ui.label(name, "small");
            HBox.setHgrow(label, Priority.ALWAYS);
            Label state = Ui.chip(mod.enabled() ? "已启用" : "已禁用",
                    mod.enabled() ? "chip-green" : "chip-red");
            Button toggle = new Button(mod.enabled() ? "禁用" : "启用");
            toggle.getStyleClass().addAll("btn", "btn-small");
            toggle.setOnAction(event -> {
                toggle(mod.file(), mod.enabled());
                shell.close();
                rebuild();
            });
            Button delete = new Button("删除");
            delete.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            delete.setOnAction(event -> {
                delete(mod.file());
                shell.close();
                rebuild();
            });
            row.getChildren().addAll(label, state, toggle, delete);
            rows.getChildren().add(row);
        }
        shell.setContent(rows);
        Button close = new Button("关闭");
        close.getStyleClass().add("btn");
        close.setOnAction(event -> shell.close());
        shell.setFooter(close);
        shell.showAndWait();
    }

    private javafx.scene.Node modIcon(byte[] bytes) {
        try {
            if (bytes != null && bytes.length > 0) {
                javafx.scene.image.Image image = new javafx.scene.image.Image(
                        new java.io.ByteArrayInputStream(bytes), 72, 72, true, true);
                if (!image.isError()) {
                    javafx.scene.image.ImageView view = new javafx.scene.image.ImageView(image);
                    view.setFitWidth(36);
                    view.setFitHeight(36);
                    view.setPreserveRatio(true);
                    view.setSmooth(true);
                    return view;
                }
            }
        } catch (Exception ignored) {
        }
        return Icon.of(Icon.PACKAGE, 14, Ui.TEXT_FAINT);
    }

    private void toggle(Path file, boolean enabled) {        String name = file.getFileName().toString();
        Path target = enabled
                ? file.resolveSibling(name + ".disabled")
                : file.resolveSibling(name.substring(0, name.length() - ".disabled".length()));
        try {
            Files.move(file, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            rebuild();
        } catch (Exception e) {
            Util.error(host.window(), "操作失败", e.getMessage());
        }
    }

    private void delete(Path file) {
        if (!Util.confirm(host.window(), "删除模组", "确定删除 " + file.getFileName() + " 吗？此操作不可恢复。")) {
            return;
        }
        try {
            Files.deleteIfExists(file);
            rebuild();
        } catch (Exception e) {
            Util.error(host.window(), "删除失败", e.getMessage());
        }
    }

    public void checkUpdatesNow() {
        checkUpdates();
    }

    private void checkUpdates() {
        if (current == null) {
            return;
        }
        if (current.loader() == LoaderType.VANILLA) {
            Util.info(host.window(), "无法检查更新", "原版实例没有模组，请先安装 Fabric / Forge / NeoForge 加载器。");
            return;
        }
        String loader = switch (current.loader()) {
            case FABRIC -> "fabric";
            case FORGE -> "forge";
            case NEOFORGE -> "neoforge";
            case QUILT -> "quilt";
            default -> null;
        };
        if (loader == null) {
            Util.info(host.window(), "无法检查更新", "当前加载器不支持自动更新检查。");
            return;
        }
        Path dir = modsDir();
        List<Path> jars = new ArrayList<>();
        if (Files.isDirectory(dir)) {
            try (var stream = Files.list(dir)) {
                stream.filter(Files::isRegularFile)
                        .filter(p -> p.getFileName().toString().endsWith(".jar"))
                        .forEach(jars::add);
            } catch (Exception e) {
                Util.error(host.window(), "读取模组失败", e.getMessage());
                return;
            }
        }
        if (jars.isEmpty()) {
            Util.info(host.window(), "没有模组", "当前实例的 mods 目录里没有已启用的模组。");
            return;
        }
        String mc = current.info() == null ? current.baseVersion() : current.info().baseVersion();
        host.toast("正在检查 " + jars.size() + " 个模组的更新...");
        context.io.submit(() -> {
            List<ModUpdate> found = new ArrayList<>();
            for (Path jar : jars) {
                try {
                    String hash = FileUtil.sha1(jar);
                    ModrinthClient.VersionLookup lookup = ModrinthClient.versionFromHash(hash);
                    if (lookup == null || lookup.projectId().isBlank()) {
                        continue;
                    }
                    List<ModrinthClient.Version> versions = ModrinthClient.versions(lookup.projectId(), mc, loader);
                    if (versions.isEmpty()) {
                        continue;
                    }
                    ModrinthClient.Version latest = versions.get(0);
                    if (latest.id().equals(lookup.versionId())) {
                        continue;
                    }
                    ModrinthClient.FileEntry file = ModrinthClient.primaryFile(latest);
                    if (file == null) {
                        continue;
                    }
                    String title = jar.getFileName().toString().replace(".jar", "");
                    found.add(new ModUpdate(jar, title, lookup.versionNumber(), latest.versionNumber(),
                            file.filename(), file.url(), file.sha1(), file.size()));
                } catch (Exception ignored) {
                }
            }
            Platform.runLater(() -> {
                if (found.isEmpty()) {
                    Util.info(host.window(), "模组更新", "所有已启用的模组都是最新版本。");
                    return;
                }
                new ModUpdateDialog(host.window(), found, plan ->
                        host.runTask("更新模组", progress -> applyUpdates(plan, progress), () -> {
                            rebuild();
                            host.toast("模组更新完成");
                        })).show();
            });
        });
    }

    private void applyUpdates(ModUpdateDialog.Plan plan, Progress progress) {
        Path dir = modsDir();
        for (ModUpdate update : plan.updates()) {
            Path old = update.oldFile();
            Path disabled = old.resolveSibling(old.getFileName() + ".disabled");
            boolean moved = false;
            try {
                if (Files.exists(old)) {
                    Files.move(old, disabled, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    moved = true;
                }
                Path target = dir.resolve(update.newFilename());
                new Downloader(2).downloadAll(List.of(Downloader.Item.of(update.title(), update.newUrl(),
                        target, update.newSha1(), update.newSize())), progress);
                if (!plan.keepOld()) {
                    Files.deleteIfExists(disabled);
                }
            } catch (Exception e) {
                if (moved) {
                    try {
                        Files.move(disabled, old, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    } catch (Exception ignored) {
                    }
                }
                throw new RuntimeException("更新 " + update.title() + " 失败: " + e.getMessage(), e);
            }
        }
    }

    private void addMods() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("选择模组文件");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter("模组文件", "*.jar"));
        List<File> files = chooser.showOpenMultipleDialog(host.window());
        if (files == null || files.isEmpty()) {
            return;
        }
        Path dir = modsDir();
        int count = 0;
        for (File file : files) {
            try {
                Files.createDirectories(dir);
                Files.copy(file.toPath(), dir.resolve(file.getName()),
                        java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                count++;
            } catch (Exception e) {
                Util.error(host.window(), "添加失败", e.getMessage());
            }
        }
        rebuild();
        host.toast("已添加 " + count + " 个模组");
    }
}
