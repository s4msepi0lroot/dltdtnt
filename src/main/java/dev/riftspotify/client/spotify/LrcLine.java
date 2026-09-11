package dev.riftspotify.client.spotify;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public record LrcLine(long timeMs, String text) {
    private static final Pattern TIMESTAMP = Pattern.compile("\\[(\\d+):(\\d{2})(?:\\.(\\d{1,3}))?\\]");

    public static List<LrcLine> parse(String lrc) {
        List<LrcLine> result = new ArrayList<>();
        if (lrc == null || lrc.isBlank()) return result;
        for (String line : lrc.split("\\R")) {
            Matcher matcher = TIMESTAMP.matcher(line);
            List<Long> timestamps = new ArrayList<>();
            int end = 0;
            while (matcher.find()) {
                long fraction = matcher.group(3) == null ? 0 : Long.parseLong((matcher.group(3) + "00").substring(0, 3));
                timestamps.add((Long.parseLong(matcher.group(1)) * 60 + Long.parseLong(matcher.group(2))) * 1000 + fraction);
                end = matcher.end();
            }
            String text = line.substring(end).trim();
            if (!text.isBlank()) for (long timestamp : timestamps) result.add(new LrcLine(timestamp, text));
        }
        result.sort(Comparator.comparingLong(LrcLine::timeMs));
        return result;
    }

    public static int activeIndex(List<LrcLine> lines, long positionMs) {
        int index = -1;
        for (int i = 0; i < lines.size(); i++) {
            if (lines.get(i).timeMs() <= positionMs) index = i; else break;
        }
        return index;
    }
}
