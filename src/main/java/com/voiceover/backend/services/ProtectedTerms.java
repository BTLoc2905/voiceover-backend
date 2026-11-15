package com.voiceover.backend.services;

import java.io.IOException;
import java.util.*;
import java.util.regex.Pattern;

/** Single-pass, longest-first masking never rewrites a token or a larger protected name. */
final class ProtectedTerms {
    private final Map<String, String> replacements = new LinkedHashMap<>();
    private final Map<String, Integer> counts = new LinkedHashMap<>();
    private final String masked;

    ProtectedTerms(String source, List<String> terms) {
        var ordered = ContentFingerprint.terms(terms);
        String prefix = "PTERM";
        while (source.contains(prefix) || containsPrefix(ordered, prefix))
            prefix = "PTERM" + UUID.randomUUID().toString().replace("-", "");
        if (ordered.isEmpty()) { masked = source; return; }
        var matcher = Pattern.compile(String.join("|", ordered.stream().map(Pattern::quote).toList())).matcher(source);
        var tokens = new HashMap<String, String>();
        StringBuilder result = new StringBuilder();
        while (matcher.find()) {
            String term = matcher.group();
            String token = tokens.get(term);
            if (token == null) {
                token = prefix + tokens.size() + "X";
                tokens.put(term, token); replacements.put(token, term);
            }
            counts.merge(token, 1, Integer::sum);
            matcher.appendReplacement(result, token);
        }
        matcher.appendTail(result); masked = result.toString();
    }

    private static boolean containsPrefix(List<String> terms, String prefix) {
        return terms.stream().anyMatch(term -> term.contains(prefix));
    }

    String masked() { return masked; }

    String restore(String translated) throws IOException {
        for (var entry : counts.entrySet()) {
            int count = 0, offset = 0;
            while ((offset = translated.indexOf(entry.getKey(), offset)) >= 0) {
                count++; offset += entry.getKey().length();
            }
            if (count != entry.getValue())
                throw new IOException("Dịch vụ dịch làm mất hoặc thay đổi thuật ngữ bảo vệ.");
        }
        // One pass: a restored term may itself contain text resembling another token.
        if (replacements.isEmpty()) return translated;
        var matcher = Pattern.compile(String.join("|", replacements.keySet().stream().map(Pattern::quote).toList())).matcher(translated);
        return matcher.replaceAll(match -> java.util.regex.Matcher.quoteReplacement(replacements.get(match.group())));
    }
}
