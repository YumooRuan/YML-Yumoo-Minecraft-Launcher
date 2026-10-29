package dev.yml.core.i18n;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Launcher and Minecraft language registry (including the in-game easter egg languages). */
public final class Language {

    public record Entry(String code, String name, String englishName, boolean launcher,
                        boolean easterEgg) {
    }

    private static final List<Entry> ALL = build();

    private Language() {
    }

    public static List<Entry> all() {
        return ALL;
    }

    public static List<Entry> launcherLanguages() {
        List<Entry> list = new ArrayList<>();
        for (Entry entry : ALL) {
            if (entry.launcher()) {
                list.add(entry);
            }
        }
        return list;
    }

    public static Entry byCode(String code) {
        if (code == null) {
            return null;
        }
        for (Entry entry : ALL) {
            if (entry.code().equalsIgnoreCase(code)) {
                return entry;
            }
        }
        return null;
    }

    /** Launcher code (zh_CN) to Minecraft language code (zh_cn). */
    public static String gameCode(String launcherCode) {
        if (launcherCode == null || launcherCode.isBlank()) {
            return "en_us";
        }
        return switch (launcherCode) {
            case "zh_CN" -> "zh_cn";
            case "zh_TW" -> "zh_tw";
            case "en_US" -> "en_us";
            case "ja_JP" -> "ja_jp";
            default -> launcherCode.toLowerCase(Locale.ROOT);
        };
    }

    public static String displayName(String code) {
        Entry entry = byCode(code);
        if (entry == null) {
            return "简体中文";
        }
        if (entry.englishName() == null || entry.englishName().isBlank()
                || entry.englishName().equals(entry.name())) {
            return entry.name();
        }
        return entry.name() + " / " + entry.englishName();
    }

    private static List<Entry> build() {
        List<Entry> list = new ArrayList<>();
        // Launcher UI languages first.
        list.add(new Entry("zh_CN", "简体中文", "Simplified Chinese", true, false));
        list.add(new Entry("zh_TW", "繁體中文", "Traditional Chinese", true, false));
        list.add(new Entry("en_US", "English", "English", true, false));
        list.add(new Entry("ja_JP", "日本語", "Japanese", true, false));
        add(list, "af_za", "Afrikaans", "Afrikaans");
        add(list, "ar_sa", "العربية", "Arabic");
        add(list, "ast_es", "Asturianu", "Asturian");
        add(list, "az_az", "Azərbaycanca", "Azerbaijani");
        add(list, "ba_ru", "Башҡортса", "Bashkir");
        add(list, "bar", "Boarisch", "Bavarian");
        add(list, "be_by", "Беларуская", "Belarusian");
        add(list, "bg_bg", "Български", "Bulgarian");
        add(list, "br_fr", "Brezhoneg", "Breton");
        add(list, "brb", "Brabants", "Brabantian");
        add(list, "bs_ba", "Bosanski", "Bosnian");
        add(list, "ca_es", "Català", "Catalan");
        add(list, "cs_cz", "Čeština", "Czech");
        add(list, "cy_gb", "Cymraeg", "Welsh");
        add(list, "da_dk", "Dansk", "Danish");
        add(list, "de_at", "Österreichisches Deutsch", "Austrian German");
        add(list, "de_ch", "Schweizerdeutsch", "Swiss German");
        add(list, "de_de", "Deutsch", "German");
        add(list, "el_gr", "Ελληνικά", "Greek");
        add(list, "en_au", "English (Australia)", "English (Australia)");
        add(list, "en_ca", "English (Canada)", "English (Canada)");
        add(list, "en_gb", "English (UK)", "English (UK)");
        add(list, "en_nz", "English (New Zealand)", "English (New Zealand)");
        add(list, "en_pt", "English (Pirate)", "English (Pirate)");
        add(list, "en_ud", "English (Upside Down)", "English (Upside Down)");
        add(list, "en_us", "English (US)", "English (US)");
        add(list, "enp", "English (Pirate Speak)", "English (Pirate)");
        add(list, "enws", "English (Shakespearean)", "English (Shakespearean)");
        add(list, "eo_ua", "Esperanto", "Esperanto");
        add(list, "es_ar", "Español (Argentina)", "Spanish (Argentina)");
        add(list, "es_cl", "Español (Chile)", "Spanish (Chile)");
        add(list, "es_ec", "Español (Ecuador)", "Spanish (Ecuador)");
        add(list, "es_es", "Español (España)", "Spanish (Spain)");
        add(list, "es_mx", "Español (México)", "Spanish (Mexico)");
        add(list, "es_uy", "Español (Uruguay)", "Spanish (Uruguay)");
        add(list, "es_ve", "Español (Venezuela)", "Spanish (Venezuela)");
        add(list, "et_ee", "Eesti", "Estonian");
        add(list, "eu_es", "Euskara", "Basque");
        add(list, "fa_ir", "فارسی", "Persian");
        add(list, "fi_fi", "Suomi", "Finnish");
        add(list, "fil_ph", "Filipino", "Filipino");
        add(list, "fo_fo", "Føroyskt", "Faroese");
        add(list, "fr_ca", "Français (Canada)", "French (Canada)");
        add(list, "fr_ch", "Français (Suisse)", "French (Switzerland)");
        add(list, "fr_fr", "Français", "French");
        add(list, "fra_de", "Fränkisch", "Franconian");
        add(list, "fur_it", "Furlan", "Friulian");
        add(list, "fy_nl", "Frysk", "Frisian");
        add(list, "ga_ie", "Gaeilge", "Irish");
        add(list, "gd_gb", "Gàidhlig", "Scottish Gaelic");
        add(list, "gl_es", "Galego", "Galician");
        add(list, "haw_us", "ʻŌlelo Hawaiʻi", "Hawaiian");
        add(list, "he_il", "עברית", "Hebrew");
        add(list, "hi_in", "हिन्दी", "Hindi");
        add(list, "hr_hr", "Hrvatski", "Croatian");
        add(list, "hu_hu", "Magyar", "Hungarian");
        add(list, "hy_am", "Հայերեն", "Armenian");
        add(list, "id_id", "Bahasa Indonesia", "Indonesian");
        add(list, "ig_ng", "Igbo", "Igbo");
        add(list, "io_en", "Ido", "Ido");
        add(list, "is_is", "Íslenska", "Icelandic");
        add(list, "isv", "Medžuslovjansky", "Interslavic");
        add(list, "it_it", "Italiano", "Italian");
        add(list, "ja_jp", "日本語", "Japanese");
        add(list, "jbo_en", "Lojban", "Lojban");
        add(list, "ka_ge", "ქართული", "Georgian");
        add(list, "kk_kz", "Қазақша", "Kazakh");
        add(list, "kn_in", "ಕನ್ನಡ", "Kannada");
        add(list, "ko_kr", "한국어", "Korean");
        add(list, "ksh", "Kölsch", "Kölsch");
        add(list, "kw_gb", "Kernewek", "Cornish");
        add(list, "la_la", "Latina", "Latin");
        add(list, "lb_lu", "Lëtzebuergesch", "Luxembourgish");
        add(list, "li_li", "Limburgs", "Limburgish");
        add(list, "lol_us", "LOLCAT", "LOLCAT");
        add(list, "lt_lt", "Lietuvių", "Lithuanian");
        add(list, "lv_lv", "Latviešu", "Latvian");
        add(list, "lzh", "文言文", "Literary Chinese");
        add(list, "mk_mk", "Македонски", "Macedonian");
        add(list, "mn_mn", "Монгол", "Mongolian");
        add(list, "ms_my", "Bahasa Melayu", "Malay");
        add(list, "mt_mt", "Malti", "Maltese");
        add(list, "nah", "Nahuatl", "Nahuatl");
        add(list, "nb_no", "Norsk bokmål", "Norwegian Bokmål");
        add(list, "nds_de", "Plattdüütsch", "Low German");
        add(list, "nl_be", "Vlaams", "Flemish");
        add(list, "nl_nl", "Nederlands", "Dutch");
        add(list, "nn_no", "Norsk nynorsk", "Norwegian Nynorsk");
        add(list, "no_no", "Norsk", "Norwegian");
        add(list, "oc_fr", "Occitan", "Occitan");
        add(list, "ovd", "Övdalsk", "Elfdalian");
        add(list, "pl_pl", "Polski", "Polish");
        add(list, "pt_br", "Português (Brasil)", "Portuguese (Brazil)");
        add(list, "pt_pt", "Português (Portugal)", "Portuguese (Portugal)");
        add(list, "qya_aa", "Quenya", "Quenya");
        add(list, "ro_ro", "Română", "Romanian");
        add(list, "rpr", "Русский (дореформенный)", "Pre-reform Russian");
        add(list, "ru_ru", "Русский", "Russian");
        add(list, "ry_ua", "Русинськый", "Rusyn");
        add(list, "sah_sah", "Сахалыы", "Sakha");
        add(list, "se_no", "Davvisámegiella", "Northern Sami");
        add(list, "sk_sk", "Slovenčina", "Slovak");
        add(list, "sl_si", "Slovenščina", "Slovenian");
        add(list, "so_so", "Soomaali", "Somali");
        add(list, "sq_al", "Shqip", "Albanian");
        add(list, "sr_sp", "Српски", "Serbian");
        add(list, "sv_se", "Svenska", "Swedish");
        add(list, "sxu", "Säggs'sch", "Upper Saxon");
        add(list, "szl", "Ślōnski", "Silesian");
        add(list, "ta_in", "தமிழ்", "Tamil");
        add(list, "th_th", "ไทย", "Thai");
        add(list, "tl_ph", "Tagalog", "Tagalog");
        add(list, "tlh_aa", "tlhIngan Hol", "Klingon");
        add(list, "tok", "toki pona", "Toki Pona");
        add(list, "tr_tr", "Türkçe", "Turkish");
        add(list, "tt_ru", "Татарча", "Tatar");
        add(list, "tzo_mx", "Batsʼi kʼop", "Tzotzil");
        add(list, "uk_ua", "Українська", "Ukrainian");
        add(list, "val_es", "Valencià", "Valencian");
        add(list, "vec_it", "Vèneto", "Venetian");
        add(list, "vi_vn", "Tiếng Việt", "Vietnamese");
        add(list, "yi_de", "ייִדיש", "Yiddish");
        add(list, "yo_ng", "Yorùbá", "Yoruba");
        add(list, "zgh", "ⵜⴰⵎⴰⵣⵉⵖⵜ", "Standard Moroccan Tamazight");
        add(list, "zh_cn", "简体中文（中国）", "Chinese (Simplified)");
        add(list, "zh_hk", "繁體中文（香港）", "Chinese (Hong Kong)");
        add(list, "zh_tw", "繁體中文（台灣）", "Chinese (Traditional)");
        return List.copyOf(list);
    }

    private static void add(List<Entry> list, String code, String name, String english) {
        boolean easter = switch (code) {
            case "en_ud", "enp", "enws", "lol_us", "tok", "qya_aa", "tlh_aa", "lzh", "isv", "rpr",
                 "sxu", "bar", "ksh", "ovd", "jbo_en" -> true;
            default -> false;
        };
        list.add(new Entry(code, name, english, false, easter));
    }
}
