package dev.vitalstages.chat;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.function.IntUnaryOperator;

/** Только проверенные буквальные строки. Нет шаблонизатора, команд или Component-десериализации. */
public record PhraseBook(List<String> phrases) {
    public static final int MAX_PHRASES = 128, MAX_CODE_POINTS = 160;
    public PhraseBook {
        if (phrases == null || phrases.size() > MAX_PHRASES) throw new IllegalArgumentException("phrases: максимум 128 строк");
        var unique = new LinkedHashSet<String>();
        for (int i = 0; i < phrases.size(); i++) {
            String p = phrases.get(i);
            if (p == null) throw new IllegalArgumentException("phrases[" + i + "]: нужна строка");
            // Даже escaped переводы строк/управляющие символы запрещены после JSON decoding.
            if (p.codePoints().anyMatch(PhraseBook::forbidden))
                throw new IllegalArgumentException("phrases[" + i + "]: запрещён управляющий символ");
            p = p.strip();
            if (p.isEmpty() || p.startsWith("/") || p.codePointCount(0, p.length()) > MAX_CODE_POINTS)
                throw new IllegalArgumentException("phrases[" + i + "]: 1–160 символов, без начального /");
            unique.add(p);
        }
        phrases = List.copyOf(unique);
    }
    private static boolean forbidden(int cp) {
        int type = Character.getType(cp);
        return cp == 0x00A7 || type == Character.CONTROL || type == Character.FORMAT
                || type == Character.SURROGATE || type == Character.LINE_SEPARATOR || type == Character.PARAGRAPH_SEPARATOR;
    }
    public static PhraseBook empty() { return new PhraseBook(List.of()); }
    public Optional<String> choose(String previous, IntUnaryOperator randomBelowBound) {
        if (phrases.isEmpty()) return Optional.empty();
        int skip = phrases.size() > 1 ? phrases.indexOf(previous) : -1;
        int bound = phrases.size() - (skip >= 0 ? 1 : 0);
        int index = randomBelowBound.applyAsInt(bound);
        if (index < 0 || index >= bound) throw new IllegalArgumentException("RNG вернул индекс вне диапазона");
        if (skip >= 0 && index >= skip) index++;
        return Optional.of(phrases.get(index));
    }
}
