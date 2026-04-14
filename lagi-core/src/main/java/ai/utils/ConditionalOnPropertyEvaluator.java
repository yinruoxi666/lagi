package ai.utils;

import ai.annotation.ConditionalOnProperty;
import ai.config.ContextLoader;


final class ConditionalOnPropertyEvaluator {

    private ConditionalOnPropertyEvaluator() {
    }

    static boolean matches(ConditionalOnProperty ann) {
        String[] names = ann.name().length > 0 ? ann.name() : ann.value();
        if (names.length == 0) {
            return true;
        }
        String prefix = ann.prefix().trim();
        if (!prefix.isEmpty() && !prefix.endsWith(".")) {
            prefix = prefix + ".";
        }
        for (String n : names) {
            if (n == null || n.isEmpty()) {
                continue;
            }
            String key = prefix + n.trim();
            if (!propertyMatches(key, ann.havingValue(), ann.matchIfMissing())) {
                return false;
            }
        }
        return true;
    }

    private static boolean propertyMatches(String key, String havingValue, boolean matchIfMissing) {
        String val = ContextLoader.getProperties(key);
        if (val == null) {
            return matchIfMissing;
        }
        String trimmed = val.trim();
        if (havingValue == null || havingValue.isEmpty()) {
            return !"false".equalsIgnoreCase(trimmed);
        }
        return havingValue.equalsIgnoreCase(trimmed);
    }
}
