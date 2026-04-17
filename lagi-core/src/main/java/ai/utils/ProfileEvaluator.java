package ai.utils;

import ai.annotation.Profile;
import ai.config.ContextLoader;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;


final class ProfileEvaluator {

    private ProfileEvaluator() {
    }

    static boolean matches(Profile ann) {
        if (ann == null) {
            return true;
        }
        String[] declared = ann.value();
        if (declared == null || declared.length == 0) {
            return true;
        }
        Set<String> active = resolveActiveProfiles();
        for (String p : declared) {
            if (p == null || p.isEmpty()) {
                continue;
            }
            if (active.contains(p.trim())) {
                return true;
            }
        }
        return false;
    }


    static Set<String> resolveActiveProfiles() {
        String raw = firstNonBlank(
                System.getProperty("lagi.profiles.active"),
                ContextLoader.getProperties("lagi.profiles.active")
        );
        Set<String> set = new LinkedHashSet<>();
        if (raw != null) {
            for (String part : raw.split(",")) {
                String t = part.trim();
                if (!t.isEmpty()) {
                    set.add(t);
                }
            }
        }
        if (set.isEmpty()) {
            set.add("default");
        }
        return Collections.unmodifiableSet(set);
    }

    private static String firstNonBlank(String... candidates) {
        if (candidates == null) {
            return null;
        }
        for (String s : candidates) {
            if (s != null && !s.trim().isEmpty()) {
                return s.trim();
            }
        }
        return null;
    }
}
