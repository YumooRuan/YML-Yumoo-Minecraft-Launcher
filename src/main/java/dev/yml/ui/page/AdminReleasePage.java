package dev.yml.ui.page;

import dev.yml.core.admin.AdminAuth;
import dev.yml.core.json.Json;
import dev.yml.core.update.ReleaseBuilder;
import dev.yml.ui.AppContext;
import dev.yml.ui.Page;
import dev.yml.ui.Ui;
import dev.yml.ui.UiHost;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.TextArea;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.nio.file.Files;
import java.nio.file.Path;

/** Admin page: package a release and write update.json (password gated). */
public final class AdminReleasePage implements Page {

    private final AppContext context;
    private final UiHost host;
    private final BorderPane view = new BorderPane();
    private final TextField version = new TextField("0.1.1");
    private final TextField baseUrl = new TextField("https://your-domain.example/yml");
    private final TextArea notes = new TextArea();
    private final TextArea result = new TextArea();

    public AdminReleasePage(AppContext context, UiHost host) {
        this.context = context;
        this.host = host;
        build();
    }

    private void build() {
        view.setPadding(new Insets(18, 20, 14, 20));
        HBox header = Ui.row(10, Ui.h1("发布新版本"), Ui.small("生成发布包与 update.json，发布前需验证管理员密码"));
        header.setAlignment(Pos.CENTER_LEFT);

        notes.setPrefHeight(90);
        notes.setPromptText("本次更新内容（会显示在更新提示里）");
        result.setEditable(false);
        result.setPrefHeight(180);
        result.getStyleClass().add("mono");

        Button publish = new Button("验证密码并生成发布包");
        publish.getStyleClass().addAll("btn", "btn-primary");
        publish.setOnAction(event -> generate());

        VBox form = new VBox(10,
                row("版本号", version),
                row("下载地址前缀", baseUrl),
                Ui.label("更新说明", "tiny", "faint"),
                notes,
                publish,
                result);
        form.getStyleClass().add("card");
        form.setPadding(new Insets(16, 18, 16, 18));
        view.setTop(new VBox(10, header));
        view.setCenter(form);
    }

    private HBox row(String label, TextField field) {
        javafx.scene.control.Label name = Ui.label(label, "small", "muted");
        name.setMinWidth(120);
        HBox box = new HBox(10, name, field);
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(field, Priority.ALWAYS);
        return box;
    }

    private Path installDir() {
        try {
            Path location = Path.of(AdminReleasePage.class.getProtectionDomain()
                    .getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(location)) {
                return location.getParent();
            }
        } catch (Exception ignored) {
        }
        Path dist = Path.of(System.getProperty("user.dir"), "dist", "YML");
        return Files.isDirectory(dist) ? dist : Path.of(System.getProperty("user.dir"));
    }

    private void generate() {
        if (!AdminAuth.prompt(host.window(), context.dataRoot, AdminAuth.Action.VERSION)) {
            result.setText("密码验证未通过，未生成发布包。");
            return;
        }
        try {
            String ver = version.getText().trim();
            String urlBase = baseUrl.getText().trim();
            if (!ver.matches("[0-9]+(\\.[0-9]+){1,3}")) {
                throw new IllegalArgumentException("版本号格式应为 0.1.1");
            }
            Path install = installDir();
            Path outDir = context.dataRoot.resolve("releases");
            Path zip = outDir.resolve("yml-" + ver + ".zip");
            ReleaseBuilder.zip(install, zip);
            String url = (urlBase.endsWith("/") ? urlBase : urlBase + "/") + zip.getFileName();
            var manifest = ReleaseBuilder.manifest(ver, url, zip, notes.getText());
            Path json = outDir.resolve("update.json");
            ReleaseBuilder.writeManifest(json, manifest);
            String zipHash = sha256(zip);
            String jsonHash = sha256(json);
            Files.writeString(outDir.resolve("SHA256SUMS.txt"),
                    zipHash + "  " + zip.getFileName() + "\n"
                            + jsonHash + "  update.json\n");
            result.setText("安装目录: " + install + "\n"
                    + "发布包: " + zip + "\n"
                    + "清单: " + json + "\n\n"
                    + "SHA-256（请贴到下载页）:\n"
                    + zipHash + "  " + zip.getFileName() + "\n"
                    + jsonHash + "  update.json\n\n"
                    + Json.write(manifest, true) + "\n\n"
                    + "把 yml-" + ver + ".zip、update.json、SHA256SUMS.txt 上传到 " + urlBase);
        } catch (Exception e) {
            result.setText("生成失败: " + e.getMessage());
        }
    }

    private static String sha256(Path file) throws Exception {
        java.security.MessageDigest digest = java.security.MessageDigest.getInstance("SHA-256");
        try (var in = Files.newInputStream(file)) {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = in.read(buffer)) >= 0) {
                digest.update(buffer, 0, read);
            }
        }
        StringBuilder builder = new StringBuilder();
        for (byte value : digest.digest()) {
            builder.append(String.format("%02x", value));
        }
        return builder.toString();
    }

    @Override
    public Node view() {
        return view;
    }

    @Override
    public void onShow() {
    }
}
