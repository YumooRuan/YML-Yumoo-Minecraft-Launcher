package dev.yml.core.admin;

import dev.yml.ui.Ui;
import dev.yml.ui.component.DialogShell;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.layout.VBox;
import javafx.stage.Window;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;

/**
 * Admin publish gate with separate passwords for version releases and announcements.
 * Defaults are overridable via system properties, environment variables or data files.
 */
public final class AdminAuth {

    public enum Action {
        VERSION("发布新版本", "yml.admin.version.password", "YML_ADMIN_VERSION_PASSWORD",
                "admin-version-password.txt", ""),
        ANNOUNCEMENT("发布公告", "yml.admin.announcement.password", "YML_ADMIN_ANNOUNCEMENT_PASSWORD",
                "admin-announcement-password.txt", "");

        private final String label;
        private final String property;
        private final String environment;
        private final String fileName;
        private final String fallback;

        Action(String label, String property, String environment, String fileName, String fallback) {
            this.label = label;
            this.property = property;
            this.environment = environment;
            this.fileName = fileName;
            this.fallback = fallback;
        }
    }

    public static final String APPLY_EMAIL = "maintainer@example.com";

    private AdminAuth() {
    }

    public static boolean verify(Action action, Path dataRoot, String input) {
        if (input == null) {
            return false;
        }
        String expected = password(action, dataRoot);
        if (expected == null || expected.isBlank()) {
            return false;
        }
        return MessageDigest.isEqual(
                expected.getBytes(StandardCharsets.UTF_8),
                input.trim().getBytes(StandardCharsets.UTF_8));
    }

    public static String password(Action action, Path dataRoot) {
        String fromProperty = System.getProperty(action.property);
        if (fromProperty != null && !fromProperty.isBlank()) {
            return fromProperty;
        }
        String fromEnv = System.getenv(action.environment);
        if (fromEnv != null && !fromEnv.isBlank()) {
            return fromEnv;
        }
        try {
            Path file = dataRoot.resolve(action.fileName);
            if (Files.isRegularFile(file)) {
                String text = Files.readString(file, StandardCharsets.UTF_8).trim();
                if (!text.isBlank()) {
                    return text;
                }
            }
        } catch (Exception ignored) {
        }
        return action.fallback;
    }

    /** Password dialog shown before every publish action. */
    public static boolean prompt(Window owner, Path dataRoot, Action action) {
        DialogShell shell = new DialogShell(owner, "申请管理员端密码", 540, 430);
        if (shell.stage.getScene() != null) {
            shell.stage.getScene().getRoot().getStyleClass().add("admin-auth");
        }
        Label title = Ui.h2("申请管理员端密码");
        Label hint = Ui.label(action.label + "前必须验证管理员密码。", "small", "muted");
        Label email = Ui.label("申请密码请发邮件到 " + APPLY_EMAIL, "small", "accent");
        PasswordField field = new PasswordField();
        field.setPromptText("管理员密码");
        Label error = Ui.label("", "small", "red");
        VBox content = new VBox(10, title, hint, email, field, error);
        content.setPadding(new Insets(4, 0, 0, 0));
        shell.setContent(content);

        boolean[] ok = {false};
        Button cancel = new Button("取消");
        cancel.getStyleClass().add("btn");
        cancel.setOnAction(event -> shell.close());
        Button verify = new Button("验证并继续");
        verify.getStyleClass().addAll("btn", "btn-primary");
        verify.setDefaultButton(true);
        Runnable submit = () -> {
            if (AdminAuth.verify(action, dataRoot, field.getText())) {
                ok[0] = true;
                shell.close();
            } else {
                error.setText("密码错误，请重新输入。");
                field.clear();
                field.requestFocus();
            }
        };
        verify.setOnAction(event -> submit.run());
        field.setOnAction(event -> submit.run());
        shell.setFooter(cancel, verify);
        shell.showAndWait();
        return ok[0];
    }
}
