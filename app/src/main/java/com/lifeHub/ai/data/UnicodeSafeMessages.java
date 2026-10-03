package com.lifeHub.ai.data;

import com.google.ai.edge.litertlm.Conversation;
import com.google.ai.edge.litertlm.LiteRtLmJni;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.lang.reflect.Field;

/**
 * Text-only compatibility adapter for the pinned LiteRT-LM 0.10.2 JNI boundary.
 * Its JSON parser expects standard UTF-8, but GetStringUTFChars emits modified
 * UTF-8 for surrogate pairs. Escape JSON, not user text: native JSON decoding
 * restores the original code points before tokenization. No tool calls execute.
 * Remove this shim when upgrading to an SDK with a verified Unicode-safe API.
 */
public final class UnicodeSafeMessages {
    private UnicodeSafeMessages() {}

    public static String messageJson(String text) {
        // Reject ill-formed UTF-16 in Java, before the native JSON parser sees it.
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (++i >= text.length() || !Character.isLowSurrogate(text.charAt(i)))
                    throw new IllegalArgumentException("Unpaired Unicode surrogate");
            } else if (Character.isLowSurrogate(c)) {
                throw new IllegalArgumentException("Unpaired Unicode surrogate");
            }
        }
        JsonObject content = new JsonObject();
        content.addProperty("type", "text");
        content.addProperty("text", text);
        JsonArray contents = new JsonArray();
        contents.add(content);
        JsonObject message = new JsonObject();
        message.addProperty("role", "user");
        message.add("content", contents);
        String json = message.toString();
        StringBuilder escaped = new StringBuilder(json.length());
        for (int i = 0; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c > 0x7f) {
                escaped.append("\\u");
                for (int shift = 12; shift >= 0; shift -= 4)
                    escaped.append("0123456789abcdef".charAt((c >> shift) & 15));
            } else escaped.append(c);
        }
        return escaped.toString();
    }

    private static long handle(Conversation conversation) {
        if (!conversation.isAlive() || conversation.getAutomaticToolCalling())
            throw new IllegalStateException("A live conversation with tool execution disabled is required");
        try {
            Field field = Conversation.class.getDeclaredField("handle");
            field.setAccessible(true);
            return field.getLong(conversation);
        } catch (ReflectiveOperationException e) {
            // Fail recoverably if the pinned SDK contract changes; never guess a pointer.
            throw new IllegalStateException("Unsupported LiteRT-LM conversation contract", e);
        }
    }

    public static String textFromResponse(String response) {
        JsonObject message = JsonParser.parseString(response).getAsJsonObject();
        if (message.has("tool_calls")) throw new IllegalStateException("Tool execution is unsupported");
        StringBuilder text = new StringBuilder();
        if (message.has("content")) for (JsonElement element : message.getAsJsonArray("content")) {
            JsonObject content = element.getAsJsonObject();
            if (!"text".equals(content.get("type").getAsString()))
                throw new IllegalStateException("Only text output is supported");
            text.append(content.get("text").getAsString());
        }
        return text.toString();
    }

    public static String send(Conversation conversation, String text) {
        return textFromResponse(LiteRtLmJni.INSTANCE.nativeSendMessage(handle(conversation), messageJson(text), "{}"));
    }

    public interface Callback {
        void onText(String text);
        void onDone();
        void onError(Throwable error);
    }

    public static void sendAsync(Conversation conversation, String text, Callback callback) {
        LiteRtLmJni.INSTANCE.nativeSendMessageAsync(handle(conversation), messageJson(text), "{}",
            new LiteRtLmJni.JniMessageCallback() {
                public void onMessage(String message) {
                    try { callback.onText(textFromResponse(message)); }
                    catch (Exception e) { callback.onError(e); }
                }
                public void onDone() { callback.onDone(); }
                public void onError(int status, String message) {
                    callback.onError(new IllegalStateException("Inference error " + status + ": " + message));
                }
            });
    }
}
