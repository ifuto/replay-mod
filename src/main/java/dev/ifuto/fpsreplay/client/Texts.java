package dev.ifuto.fpsreplay.client;

import com.google.gson.JsonParser;
import com.mojang.serialization.JsonOps;
import net.minecraft.text.Text;
import net.minecraft.text.TextCodecs;

/**
 * Text &harr; JSON helpers. In 1.21.6+ the old {@code Text.Serialization} was
 * removed; serialization now goes through {@link TextCodecs#CODEC}.
 */
public final class Texts {
    private Texts() {
    }

    /** Serialize a {@link Text} to a JSON string (falls back to plain text). */
    public static String toJson(Text text) {
        if (text == null) {
            return null;
        }
        try {
            return TextCodecs.CODEC.encodeStart(JsonOps.INSTANCE, text)
                    .result()
                    .map(Object::toString)
                    .orElse(text.getString());
        } catch (Throwable t) {
            return text.getString();
        }
    }

    /** Parse a JSON string into a {@link Text} (falls back to a literal). */
    public static Text fromJson(String json) {
        if (json == null || json.isEmpty()) {
            return Text.empty();
        }
        try {
            return TextCodecs.CODEC.decode(JsonOps.INSTANCE, JsonParser.parseString(json))
                    .result()
                    .map(p -> p.getFirst())
                    .orElse(Text.literal(json));
        } catch (Throwable t) {
            return Text.literal(json);
        }
    }
}
