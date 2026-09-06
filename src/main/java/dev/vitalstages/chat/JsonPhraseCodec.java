package dev.vitalstages.chat;

import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import java.io.IOException;
import java.io.StringReader;
import java.nio.ByteBuffer;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;

/** Строгий ограниченный JSON. Lenient parsing, неизвестные поля, дубликаты ключей и хвост запрещены. */
public final class JsonPhraseCodec {
    public static final int MAX_FILE_BYTES = 65536;
    private JsonPhraseCodec() {}
    public static PhraseBook parse(byte[] bytes) throws IOException {
        if (bytes.length > MAX_FILE_BYTES) throw new IllegalArgumentException("JSON: максимум 64 KiB");
        String json = StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
        if (json.startsWith("\uFEFF")) json = json.substring(1); // UTF-8 BOM из текстовых редакторов.
        try (JsonReader r = new JsonReader(new StringReader(json))) {
            r.setLenient(false);
            r.beginObject();
            var names = new HashSet<String>();
            var phrases = new ArrayList<String>();
            while (r.hasNext()) {
                String key = r.nextName();
                if (!names.add(key)) throw new IllegalArgumentException("JSON: повтор ключа");
                switch (key) {
                    case "version" -> {
                        if (r.peek() != JsonToken.NUMBER || !r.nextString().equals("1"))
                            throw new IllegalArgumentException("JSON: version должна быть целым числом 1");
                    }
                    case "phrases" -> {
                        r.beginArray();
                        while (r.hasNext()) {
                            if (phrases.size() >= PhraseBook.MAX_PHRASES || r.peek() != JsonToken.STRING)
                                throw new IllegalArgumentException("phrases: максимум 128 текстовых строк");
                            phrases.add(r.nextString());
                        }
                        r.endArray();
                    }
                    default -> throw new IllegalArgumentException("JSON: разрешены только version и phrases");
                }
            }
            r.endObject();
            if (!names.contains("version") || !names.contains("phrases") || r.peek() != JsonToken.END_DOCUMENT)
                throw new IllegalArgumentException("JSON: нужны version и phrases, без данных после объекта");
            return new PhraseBook(phrases);
        }
    }
}
