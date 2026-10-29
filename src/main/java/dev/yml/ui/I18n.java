package dev.yml.ui;

import dev.yml.core.i18n.Language;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Labeled;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Lightweight UI translation: source strings are Simplified Chinese keys; any Labeled node whose
 * text matches a key is translated in place. Untranslated strings simply stay Chinese.
 */
public final class I18n {

    private static String language = "zh_CN";
    private static final Map<String, String[]> TABLE = new LinkedHashMap<>();

    private I18n() {
    }

    public static void setLanguage(String code) {
        language = code == null || code.isBlank() ? "zh_CN" : code;
    }

    public static String language() {
        return language;
    }

    public static String gameCode() {
        return Language.gameCode(language);
    }

    public static String t(String chinese) {
        if (chinese == null || "zh_CN".equals(language)) {
            return chinese;
        }
        String[] row = TABLE.get(chinese);
        if (row == null) {
            return chinese;
        }
        return switch (language) {
            case "zh_TW" -> row[0];
            case "en_US" -> row[1];
            case "ja_JP" -> row[2];
            default -> chinese;
        };
    }

    /** Translates a node tree in place, remembering the original Chinese key per node. */
    public static void localize(Node node) {
        if (node == null) {
            return;
        }
        if (node instanceof Labeled labeled) {
            String text = labeled.getText();
            Object key = labeled.getProperties().get("i18n.key");
            if (key == null && text != null && !text.isBlank() && TABLE.containsKey(text)) {
                labeled.getProperties().put("i18n.key", text);
                key = text;
            }
            if (key != null) {
                labeled.setText(t((String) key));
            }
        }
        if (node instanceof javafx.scene.control.ScrollPane scroll) {
            localize(scroll.getContent());
        }
        if (node instanceof javafx.scene.control.TabPane tabs) {
            for (javafx.scene.control.Tab tab : tabs.getTabs()) {
                localize(tab.getContent());
            }
        }
        if (node instanceof Parent parent) {
            for (Node child : parent.getChildrenUnmodifiable()) {
                localize(child);
            }
        }
    }

    private static void put(String zh, String tw, String en, String ja) {
        TABLE.put(zh, new String[]{tw, en, ja});
    }

    static {
        // ---------------------------------------------------------- sidebar
        put("启动", "啟動", "Launch", "起動");
        put("主页", "主頁", "Home", "ホーム");
        put("版本管理", "版本管理", "Versions", "バージョン管理");
        put("下载", "下載", "Download", "ダウンロード");
        put("键位设置", "按鍵設定", "Key Binds", "キー設定");
        put("更多", "更多", "More", "その他");
        put("模组管理", "模組管理", "Mods", "MOD 管理");
        put("资源包", "資源包", "Resource Packs", "リソースパック");
        put("账户", "帳戶", "Accounts", "アカウント");
        put("设置", "設定", "Settings", "設定");

        // ---------------------------------------------------------- settings
        put("全局启动器设置，保存后立即生效", "全域啟動器設定，儲存後立即生效",
                "Global launcher settings. Saved changes apply immediately.",
                "ランチャー全体の設定です。保存後すぐに反映されます。");
        put("外观与背景", "外觀與背景", "Appearance", "外観と背景");
        put("游戏", "遊戲", "Game", "ゲーム");
        put("关于", "關於", "About", "このアプリについて");
        put("背景", "背景", "Background", "背景");
        put("深色纯色", "深色純色", "Dark color", "ダークカラー");
        put("本地图片", "本機圖片", "Local image", "ローカル画像");
        put("桌面壁纸", "桌面壁紙", "Desktop wallpaper", "デスクトップ壁紙");
        put("选择本地图片", "選擇本機圖片", "Choose image", "画像を選択");
        put("同步桌面壁纸", "同步桌面壁紙", "Sync wallpaper", "壁紙を同期");
        put("暗化", "暗化", "Dim", "暗さ");
        put("模糊", "模糊", "Blur", "ぼかし");
        put("游戏目录", "遊戲目錄", "Game folder", "ゲームフォルダー");
        put("默认启用版本隔离", "預設啟用版本隔離", "Enable version isolation by default",
                "バージョン分離を既定で有効化");
        put("下载源", "下載源", "Download source", "ダウンロード元");
        put("自动选择", "自動選擇", "Auto", "自動選択");
        put("默认内存（参考）", "預設記憶體（參考）", "Default memory (reference)",
                "既定メモリ（参考）");
        put("Java 选择", "Java 選擇", "Java selection", "Java の選択");
        put("Java 列表", "Java 清單", "Java list", "Java 一覧");
        put("JVM 行为", "JVM 行為", "JVM behavior", "JVM の動作");
        put("全局 JVM 参数", "全域 JVM 參數", "Global JVM arguments", "グローバル JVM 引数");
        put("本地库路径", "本機函式庫路徑", "Native library path", "ネイティブライブラリのパス");
        put("自动选择（按版本推荐）", "自動選擇（依版本推薦）", "Auto (recommended per version)",
                "自動（バージョン別の推奨）");
        put("手动选择", "手動選擇", "Manual", "手動選択");
        put("重新检测", "重新偵測", "Rescan", "再検出");
        put("浏览 java.exe", "瀏覽 java.exe", "Browse java.exe", "java.exe を参照");
        put("保存设置", "儲存設定", "Save settings", "設定を保存");
        put("打开数据目录", "開啟資料目錄", "Open data folder", "データフォルダーを開く");
        put("不添加默认 JVM 参数（-Xms / -Xmx / 编码）", "不新增預設 JVM 參數（-Xms / -Xmx / 編碼）",
                "Do not add default JVM arguments (-Xms / -Xmx / encoding)",
                "既定の JVM 引数を追加しない（-Xms / -Xmx / エンコーディング）");
        put("不自动添加 JVM 优化参数（G1GC / 并行引用处理）",
                "不自動新增 JVM 最佳化參數（G1GC / 平行引用處理）",
                "Do not add JVM tuning arguments (G1GC / parallel reference processing)",
                "JVM 最適化引数を自動追加しない（G1GC / 並列参照処理）");
        put("不检查 Java 与游戏版本兼容性", "不檢查 Java 與遊戲版本相容性",
                "Skip Java / game version compatibility check", "Java とゲームの互換性チェックをしない");

        // ---------------------------------------------------------- home
        put("游戏版本", "遊戲版本", "Game versions", "ゲームバージョン");
        put("搜索版本 / 加载器", "搜尋版本 / 載入器", "Search versions / loaders",
                "バージョン / ローダーを検索");
        put("添加实例", "新增執行個體", "Add instance", "インスタンスを追加");
        put("快捷操作", "快速操作", "Quick actions", "クイック操作");
        put("启动游戏", "啟動遊戲", "Launch game", "ゲームを起動");
        put("浏览 / 切换版本", "瀏覽 / 切換版本", "Browse / switch version", "バージョンを切り替え");
        put("管理当前版本", "管理目前版本", "Manage current version", "現在のバージョンを管理");
        put("当前版本", "目前版本", "Current version", "現在のバージョン");
        put("皮肤预览", "皮膚預覽", "Skin preview", "スキンプレビュー");
        put("默认 Steve", "預設 Steve", "Default Steve", "デフォルト Steve");
        put("默认 Alex", "預設 Alex", "Default Alex", "デフォルト Alex");
        put("正版皮肤", "正版皮膚", "Premium skin", "正規版スキン");
        put("本地 PNG", "本機 PNG", "Local PNG", "ローカル PNG");
        put("内存", "記憶體", "Memory", "メモリ");
        put("模组", "模組", "Mods", "MOD");
        put("打开文件夹", "開啟資料夾", "Open folder", "フォルダーを開く");
        put("实例文件夹", "執行個體資料夾", "Instance folder", "インスタンスフォルダー");
        put("导出整合包", "匯出模組包", "Export modpack", "MOD パックを書き出す");
        put("崩溃诊断", "崩潰診斷", "Crash analysis", "クラッシュ解析");
        put("存档文件夹", "存檔資料夾", "Saves folder", "セーブフォルダー");
        put("截图文件夹", "截圖資料夾", "Screenshots folder", "スクリーンショット");
        put("资源包管理", "資源包管理", "Resource packs", "リソースパック");
        put("检查模组更新", "檢查模組更新", "Check mod updates", "MOD の更新を確認");

        // ---------------------------------------------------------- download
        put("模组加载器", "模組載入器", "Mod loaders", "MOD ローダー");
        put("内容下载", "內容下載", "Content", "コンテンツ");
        put("整合包", "模組包", "Modpacks", "MOD パック");
        put("光影", "光影", "Shaders", "シェーダー");
        put("安装", "安裝", "Install", "インストール");

        // ---------------------------------------------------------- common
        put("保存", "儲存", "Save", "保存");
        put("取消", "取消", "Cancel", "キャンセル");
        put("关闭", "關閉", "Close", "閉じる");
        put("确定", "確定", "OK", "OK");
        put("删除", "刪除", "Delete", "削除");
        put("添加", "新增", "Add", "追加");
        put("启用", "啟用", "Enable", "有効化");
        put("禁用", "停用", "Disable", "無効化");
        put("修改", "修改", "Change", "変更");
        put("清除", "清除", "Clear", "クリア");
        put("重新读取", "重新讀取", "Reload", "再読み込み");
        put("同步到其它版本", "同步到其他版本", "Sync to other versions", "他のバージョンに同期");
        put("打开日志窗口", "開啟日誌視窗", "Open log window", "ログウィンドウを開く");
        put("语言", "語言", "Language", "言語");
        put("主题色", "主題色", "Theme color", "テーマカラー");
        put("黑色（默认）", "黑色（預設）", "Black (default)", "ブラック（既定）");
        put("自动（取背景主色）", "自動（取背景主色）", "Auto (background color)",
                "自動（背景の主要色）");
        put("自定义", "自訂", "Custom", "カスタム");
        put("自动模式读取背景图主色", "自動模式讀取背景圖主色",
                "Auto uses the background image's dominant color",
                "自動モードは背景画像の主要色を使用");
        put("启动器语言", "啟動器語言", "Launcher language", "ランチャーの言語");
        put("将启动器语言同步到游戏实例（启动时写入 options.txt 的 lang）",
                "將啟動器語言同步到遊戲執行個體（啟動時寫入 options.txt 的 lang）",
                "Sync launcher language to game instances (write lang to options.txt on launch)",
                "ランチャーの言語をゲームに同期（起動時に options.txt の lang を書き込む）");
        put("浏览", "瀏覽", "Browse", "参照");
        put("启动器", "啟動器", "Launcher", "ランチャー");

        // ---------------------------------------------------------- accounts / mods
        put("离线登录", "離線登入", "Offline login", "オフラインログイン");
        put("微软登录", "微軟登入", "Microsoft login", "Microsoft ログイン");
        put("外置登录", "外置登入", "External login", "外部ログイン");
        put("添加账户", "新增帳戶", "Add account", "アカウントを追加");
        put("检查更新", "檢查更新", "Check updates", "更新を確認");
        put("公告", "公告", "Announcements", "お知らせ");
        put("公告收藏夹", "公告收藏夾", "Saved announcements", "お気に入りのお知らせ");
        put("置顶", "置頂", "Pinned", "固定");
        put("收藏", "收藏", "Favorite", "お気に入り");
        put("已收藏", "已收藏", "Favorited", "お気に入り済み");
        put("刷新", "重新整理", "Refresh", "更新");
        put("公告源", "公告來源", "Announcement feed", "お知らせの取得元");
    }
}
