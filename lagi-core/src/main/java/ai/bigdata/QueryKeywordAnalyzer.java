package ai.bigdata;

import ai.bigdata.pojo.QueryKeywordScore;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/** Small deterministic fallback used only when a backend cannot expose its analyzer. */
public final class QueryKeywordAnalyzer {
    private static final Pattern TOKEN_PATTERN = Pattern.compile(
            "[\\p{IsHan}]+|[\\p{L}\\p{N}][\\p{L}\\p{N}_.+\\-]*");

    private QueryKeywordAnalyzer() {
    }

    public static List<QueryKeywordScore> analyze(String query) {
        Map<String, Integer> frequencies = new LinkedHashMap<>();
        if (query != null) {
            Matcher matcher = TOKEN_PATTERN.matcher(query.toLowerCase(Locale.ROOT));
            while (matcher.find()) {
                String token = matcher.group().trim();
                if (!token.isEmpty()) {
                    frequencies.put(token, frequencies.getOrDefault(token, 0) + 1);
                }
            }
        }
        List<QueryKeywordScore> result = new ArrayList<>();
        for (Map.Entry<String, Integer> entry : frequencies.entrySet()) {
            result.add(QueryKeywordScore.builder()
                    .keyword(entry.getKey())
                    .queryFrequency(entry.getValue())
                    .documentFrequency(null)
                    .score(entry.getValue().doubleValue())
                    .build());
        }
        result.sort(Comparator.comparing(QueryKeywordScore::getScore).reversed()
                .thenComparing(QueryKeywordScore::getKeyword));
        return result;
    }
}
