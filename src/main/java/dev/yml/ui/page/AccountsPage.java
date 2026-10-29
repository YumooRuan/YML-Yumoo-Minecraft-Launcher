package dev.yml.ui.page;

import dev.yml.core.auth.Account;
import dev.yml.core.auth.ExternalAuth;
import dev.yml.core.auth.MicrosoftAuth;
import dev.yml.ui.AppContext;
import dev.yml.ui.Page;
import dev.yml.ui.Ui;
import dev.yml.ui.UiHost;
import dev.yml.ui.Util;
import dev.yml.ui.component.Avatar;
import dev.yml.ui.component.Icon;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.Hyperlink;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;

import java.util.concurrent.atomic.AtomicBoolean;

/** Account management: offline, external (LittleSkin / authlib-injector) and Microsoft. */
public final class AccountsPage implements Page {

    private final AppContext context;
    private final UiHost host;
    private final BorderPane view = new BorderPane();
    private final VBox list = new VBox(8);
    private final StackPane formArea = new StackPane();

    private final TextField nameField = new TextField();
    private final TextField externalRoot = new TextField(ExternalAuth.LITTLESKIN);
    private final TextField externalUser = new TextField();
    private final PasswordField externalPassword = new PasswordField();
    private final TextField clientIdField = new TextField();
    private final Label msStatus = Ui.label("", "tiny", "faint");
    private final Label msCode = Ui.label("", "h2");
    private final Hyperlink msLink = new Hyperlink("");
    private final Button msLogin = new Button("开始微软登录");
    private final Button msCancel = new Button("取消");
    private final AtomicBoolean msCancelled = new AtomicBoolean(false);

    private final Button tabOffline = new Button("离线账户");
    private final Button tabExternal = new Button("外置登录");
    private final Button tabMicrosoft = new Button("微软正版");
    private String tab = "offline";

    public AccountsPage(AppContext context, UiHost host) {
        this.context = context;
        this.host = host;
        build();
    }

    private void build() {
        view.setPadding(new Insets(18, 20, 14, 20));
        HBox header = Ui.row(10, Ui.h1("账户"),
                Ui.small("支持离线账户、LittleSkin 等外置登录（authlib-injector）与微软正版登录"));
        header.setAlignment(Pos.CENTER_LEFT);

        VBox content = new VBox(14);
        content.getChildren().add(accountListCard());
        content.getChildren().add(addAccountCard());

        javafx.scene.control.ScrollPane scroll = new javafx.scene.control.ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setHbarPolicy(javafx.scene.control.ScrollPane.ScrollBarPolicy.NEVER);
        view.setTop(new VBox(12, header));
        view.setCenter(scroll);
        BorderPane.setMargin(scroll, new Insets(12, 0, 0, 0));
        rebuild();
        selectTab("offline");
    }

    private Node accountListCard() {
        VBox card = new VBox(10);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14, 16, 14, 16));
        card.getChildren().addAll(Ui.h3("已有账户"), list);
        return card;
    }

    private Node addAccountCard() {
        VBox card = new VBox(12);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14, 16, 16, 16));

        for (Button button : new Button[]{tabOffline, tabExternal, tabMicrosoft}) {
            button.getStyleClass().add("tab");
        }
        tabOffline.setOnAction(event -> selectTab("offline"));
        tabExternal.setOnAction(event -> selectTab("external"));
        tabMicrosoft.setOnAction(event -> selectTab("microsoft"));
        HBox tabs = new HBox(6, tabOffline, tabExternal, tabMicrosoft);

        formArea.getChildren().add(offlineForm());
        card.getChildren().addAll(Ui.h3("添加账户"), tabs, formArea);
        return card;
    }

    private void selectTab(String id) {
        tab = id;
        for (Button button : new Button[]{tabOffline, tabExternal, tabMicrosoft}) {
            button.getStyleClass().remove("tab-active");
        }
        formArea.getChildren().setAll(switch (id) {
            case "external" -> externalForm();
            case "microsoft" -> microsoftForm();
            default -> offlineForm();
        });
        (switch (id) {
            case "external" -> tabExternal;
            case "microsoft" -> tabMicrosoft;
            default -> tabOffline;
        }).getStyleClass().add("tab-active");
    }

    private Node offlineForm() {
        nameField.setPromptText("玩家名称（3-16 位字母、数字或下划线）");
        nameField.setPrefWidth(280);
        Button add = new Button("添加离线账户");
        add.getStyleClass().addAll("btn", "btn-primary");
        add.setOnAction(event -> addOffline());
        nameField.setOnAction(event -> addOffline());
        Label hint = Ui.label("离线账户无需密码，适用于单机与离线服务器。", "tiny", "faint");
        return new VBox(10, hint, new HBox(8, nameField, add));
    }

    private Node externalForm() {
        externalRoot.setPrefWidth(340);
        externalUser.setPromptText("LittleSkin 邮箱 / 用户名");
        externalPassword.setPromptText("LittleSkin 密码");
        externalUser.setPrefWidth(250);
        externalPassword.setPrefWidth(250);
        Button login = new Button("登录 LittleSkin");
        login.getStyleClass().addAll("btn", "btn-primary");
        login.setOnAction(event -> loginExternal());

        Label hint = Ui.label("外置登录通过 authlib-injector 接入，兼容 LittleSkin 等 Yggdrasil 服务器。"
                + "启动时会自动下载 authlib-injector。", "tiny", "faint");
        hint.setWrapText(true);
        return new VBox(10, hint,
                row("服务器", externalRoot),
                row("账号", externalUser),
                row("密码", externalPassword),
                new HBox(8, login));
    }

    private Node microsoftForm() {
        clientIdField.setPromptText("Azure 应用 Client ID（在 portal.azure.com 注册，公开客户端）");
        clientIdField.setText(context.settings.microsoftClientId);
        clientIdField.setPrefWidth(420);
        Label hint = Ui.label("使用设备码登录：点击开始后，在浏览器打开 microsoft.com/link 输入代码即可，"
                + "无需在启动器里输入密码。", "tiny", "faint");
        hint.setWrapText(true);

        msLogin.getStyleClass().addAll("btn", "btn-primary");
        msLogin.setOnAction(event -> loginMicrosoft());
        msCancel.getStyleClass().add("btn");
        msCancel.setDisable(true);
        msCancel.setOnAction(event -> {
            msCancelled.set(true);
            msStatus.setText("正在取消...");
        });
        msCode.setText("");
        msCode.setStyle("-fx-text-fill: #4c8dff;");
        msLink.setVisible(false);
        msLink.setManaged(false);

        return new VBox(10, hint,
                row("Client ID", clientIdField),
                new HBox(8, msLogin, msCancel),
                msCode, msLink, msStatus);
    }

    private HBox row(String label, Node control) {
        Label name = Ui.label(label, "small", "muted");
        name.setMinWidth(80);
        HBox box = new HBox(10, name, control);
        box.setAlignment(Pos.CENTER_LEFT);
        HBox.setHgrow(control, Priority.ALWAYS);
        return box;
    }

    private void rebuild() {
        list.getChildren().clear();
        int selected = context.settings.selectedAccount;
        for (int i = 0; i < context.settings.accounts.size(); i++) {
            Account account = context.settings.accounts.get(i);
            boolean active = i == selected;
            HBox row = new HBox(12);
            row.getStyleClass().add(active ? "version-row-selected" : "card-soft");
            row.setPadding(new Insets(10, 14, 10, 14));
            row.setAlignment(Pos.CENTER_LEFT);

            Avatar avatar = new Avatar(account, 36);
            Label typeChip = Ui.chip(account.type.label(), switch (account.type) {
                case MICROSOFT -> "chip-green";
                case EXTERNAL -> "chip-accent";
                default -> "";
            });
            String detail = account.type == Account.Type.EXTERNAL && account.apiRoot != null
                    ? ExternalAuth.displayApiRoot(account.apiRoot)
                    : account.displayUuid();
            VBox info = new VBox(3, new HBox(8, Ui.label(account.name, "h3"), typeChip),
                    Ui.label(detail, "tiny", "faint"));
            HBox.setHgrow(info, Priority.ALWAYS);
            row.getChildren().addAll(avatar, info);

            if (!active) {
                Button use = new Button("设为当前");
                use.getStyleClass().addAll("btn", "btn-small");
                use.setOnAction(event -> {
                    context.settings.selectedAccount = context.settings.accounts.indexOf(account);
                    context.settings.save();
                    rebuild();
                    host.refreshAll();
                });
                row.getChildren().add(use);
            } else {
                row.getChildren().add(Ui.chip("当前账户", "chip-green"));
            }
            Button remove = new Button();
            remove.setGraphic(Icon.of(Icon.TRASH, 13, Ui.RED));
            remove.getStyleClass().addAll("btn", "btn-small", "btn-ghost");
            remove.setDisable(context.settings.accounts.size() <= 1);
            remove.setOnAction(event -> removeAccount(account));
            row.getChildren().add(remove);
            list.getChildren().add(row);
        }
    }

    private void addOffline() {
        String name = nameField.getText() == null ? "" : nameField.getText().trim();
        if (!name.matches("[A-Za-z0-9_]{3,16}")) {
            Util.error(host.window(), "名称无效", "玩家名称需要 3-16 位字母、数字或下划线。");
            return;
        }
        for (Account account : context.settings.accounts) {
            if (account.name.equalsIgnoreCase(name)) {
                Util.error(host.window(), "账户已存在", "已经有同名账户了。");
                return;
            }
        }
        Account account = Account.offline(name);
        addAccount(account);
        nameField.clear();
    }

    private void loginExternal() {
        String root = externalRoot.getText() == null ? "" : externalRoot.getText().trim();
        String user = externalUser.getText() == null ? "" : externalUser.getText().trim();
        String password = externalPassword.getText() == null ? "" : externalPassword.getText();
        if (user.isBlank() || password.isBlank()) {
            Util.error(host.window(), "信息不完整", "请填写外置登录的账号与密码。");
            return;
        }
        host.runTask("登录外置账户", progress -> {
            progress.note("正在连接 " + root);
            try {
                ExternalAuth.Result result = ExternalAuth.authenticate(root, user, password);
                javafx.application.Platform.runLater(() -> {
                    Account account = new Account();
                    account.type = Account.Type.EXTERNAL;
                    account.name = result.name();
                    account.uuid = result.uuid();
                    account.accessToken = result.accessToken();
                    account.apiRoot = root.isBlank() ? ExternalAuth.LITTLESKIN : root;
                    addAccount(account);
                    externalPassword.clear();
                });
            } catch (java.io.IOException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }, null);
    }

    private void loginMicrosoft() {
        String clientId = clientIdField.getText() == null ? "" : clientIdField.getText().trim();
        if (clientId.isBlank()) {
            Util.error(host.window(), "缺少 Client ID",
                    "微软登录需要一个 Azure 应用的 Client ID（公开客户端，启用设备码流程）。\n"
                            + "在 portal.azure.com 注册应用后填入即可，启动器不会上传它。");
            return;
        }
        context.settings.microsoftClientId = clientId;
        context.settings.save();
        msCancelled.set(false);
        msLogin.setDisable(true);
        msCancel.setDisable(false);
        msStatus.setText("正在请求设备码...");
        msCode.setText("");
        msLink.setVisible(false);
        msLink.setManaged(false);

        Thread thread = new Thread(() -> {
            try {
                MicrosoftAuth.MinecraftAccount account = MicrosoftAuth.login(clientId, code ->
                        Platform.runLater(() -> {
                            msCode.setText(code.userCode());
                            msLink.setText("在浏览器打开 " + code.verificationUri());
                            msLink.setVisible(true);
                            msLink.setManaged(true);
                            msLink.setOnAction(event -> Util.openUrl(code.verificationUri()));
                            msStatus.setText("等待浏览器授权（代码有效约 15 分钟）...");
                        }), null, msCancelled::get);
                Platform.runLater(() -> {
                    Account account1 = new Account();
                    account1.type = Account.Type.MICROSOFT;
                    account1.name = account.name();
                    account1.uuid = account.uuid();
                    account1.accessToken = account.accessToken();
                    account1.refreshToken = account.refreshToken();
                    account1.expiresAt = account.expiresAt();
                    addAccount(account1);
                    resetMicrosoftUi();
                    msStatus.setText("已登录 " + account.name());
                });
            } catch (Exception e) {
                Platform.runLater(() -> {
                    resetMicrosoftUi();
                    msStatus.setText("登录失败");
                    Util.error(host.window(), "微软登录失败", e.getMessage());
                });
            }
        }, "yml-microsoft-login");
        thread.setDaemon(true);
        thread.start();
    }

    private void resetMicrosoftUi() {
        msLogin.setDisable(false);
        msCancel.setDisable(true);
        msCode.setText("");
        msLink.setVisible(false);
        msLink.setManaged(false);
    }

    private void addAccount(Account account) {
        context.settings.accounts.add(account);
        context.settings.selectedAccount = context.settings.accounts.size() - 1;
        context.settings.lastPlayerName = account.name;
        context.settings.save();
        rebuild();
        host.refreshAll();
        host.toast("已添加账户 " + account.name);
    }

    private void removeAccount(Account account) {
        if (!Util.confirm(host.window(), "删除账户", "确定删除账户 " + account.name + " 吗？")) {
            return;
        }
        int index = context.settings.accounts.indexOf(account);
        context.settings.accounts.remove(account);
        if (context.settings.selectedAccount >= context.settings.accounts.size()) {
            context.settings.selectedAccount = Math.max(0, context.settings.accounts.size() - 1);
        } else if (index < context.settings.selectedAccount) {
            context.settings.selectedAccount--;
        }
        context.settings.save();
        rebuild();
        host.refreshAll();
    }

    @Override
    public Node view() {
        return view;
    }

    @Override
    public void onShow() {
        rebuild();
    }
}
