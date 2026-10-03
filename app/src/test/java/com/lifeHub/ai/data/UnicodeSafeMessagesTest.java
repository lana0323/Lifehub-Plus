package com.lifeHub.ai.data;

import com.google.gson.JsonParser;
import org.junit.Test;
import static org.junit.Assert.*;

public class UnicodeSafeMessagesTest {
    @Test public void jsonTransportPreservesEmojiRareCjkAndEscapes() {
        String input = "📱𠮷 résumé 中文 \"quoted\" \\u1234\n\u0000";
        String json = UnicodeSafeMessages.messageJson(input);
        assertTrue(json.chars().allMatch(c -> c < 128));
        assertEquals(input, JsonParser.parseString(json).getAsJsonObject()
            .getAsJsonArray("content").get(0).getAsJsonObject().get("text").getAsString());
    }
    @Test public void malformedSurrogatesFailBeforeNativeCode() {
        for (String text : new String[]{"\ud83d", "\udcf1", "\ud83dabc"}) {
            assertThrows(IllegalArgumentException.class, () -> UnicodeSafeMessages.messageJson(text));
        }
    }
    @Test public void nativeToolCallsNeverExecute() {
        assertThrows(IllegalStateException.class, () -> UnicodeSafeMessages.textFromResponse("{\"tool_calls\":[]}"));
        assertEquals("📱", UnicodeSafeMessages.textFromResponse("{\"content\":[{\"type\":\"text\",\"text\":\"📱\"}]}"));
    }
}
