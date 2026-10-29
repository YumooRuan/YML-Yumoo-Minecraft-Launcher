package dev.yml.core.game;

import dev.yml.core.json.Json;
import dev.yml.core.util.OsInfo;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/** One entry of a library's or argument's {@code rules} array. */
public final class Rule {

    public final String action;
    public final Map<String, Object> os;
    public final Map<String, Boolean> features;

    private Rule(String action, Map<String, Object> os, Map<String, Boolean> features) {
        this.action = action;
        this.os = os;
        this.features = features;
    }

    public static List<Rule> parseList(Object jsonArray) {
        List<Rule> rules = new ArrayList<>();
        if (!(jsonArray instanceof List<?> list)) {
            return rules;
        }
        for (Object element : list) {
            Map<String, Object> map = Json.objOrEmpty(element);
            Map<String, Object> os = map.get("os") instanceof Map<?, ?> m ? Json.obj(m) : null;
            Map<String, Boolean> features = null;
            if (map.get("features") instanceof Map<?, ?> fm) {
                features = new LinkedHashMap<>();
                for (var e : Json.obj(fm).entrySet()) {
                    features.put(e.getKey(), Json.bool(e.getValue(), false));
                }
            }
            rules.add(new Rule(Json.str(map.get("action"), "allow"), os, features));
        }
        return rules;
    }

    public boolean matches(Map<String, Boolean> activeFeatures) {
        if (os != null) {
            String name = Json.str(os.get("name"));
            if (name != null && !name.equals(OsInfo.name())) {
                return false;
            }
            String arch = Json.str(os.get("arch"));
            if (arch != null && !arch.equals(OsInfo.arch())) {
                return false;
            }
            String version = Json.str(os.get("version"));
            if (version != null) {
                try {
                    if (!Pattern.compile(version).matcher(OsInfo.version()).find()) {
                        return false;
                    }
                } catch (RuntimeException ignored) {
                }
            }
        }
        if (features != null) {
            for (var e : features.entrySet()) {
                boolean actual = activeFeatures != null && activeFeatures.getOrDefault(e.getKey(), false);
                if (actual != e.getValue()) {
                    return false;
                }
            }
        }
        return true;
    }

    /** Standard Mojang semantics: no rules means allowed, otherwise the last matching rule wins. */
    public static boolean allows(List<Rule> rules, Map<String, Boolean> features) {
        if (rules == null || rules.isEmpty()) {
            return true;
        }
        String result = null;
        for (Rule rule : rules) {
            if (rule.matches(features)) {
                result = rule.action;
            }
        }
        return "allow".equals(result);
    }

    public static Map<String, Boolean> defaultFeatures(boolean customResolution) {
        Map<String, Boolean> features = new LinkedHashMap<>();
        features.put("is_demo_user", false);
        features.put("has_custom_resolution", customResolution);
        features.put("has_quick_plays_support", false);
        features.put("is_quick_play_singleplayer", false);
        features.put("is_quick_play_multiplayer", false);
        features.put("is_quick_play_realms", false);
        return features;
    }
}
