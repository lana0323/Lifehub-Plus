import com.google.ai.edge.litertlm.*;
import com.lifeHub.ai.data.UnicodeSafeMessages;
import com.google.gson.*;
import java.nio.file.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;

/** Host compatibility evaluation only. Never label these timings as phone measurements. */
public class NativeRunner {
    public static void main(String[] args) throws Exception {
        Gson gson = new GsonBuilder().serializeNulls().create();
        JsonArray cases = JsonParser.parseString(Files.readString(Path.of(args[1]))).getAsJsonArray();
        Path output = Path.of(args[2]);
        JsonArray rows = Files.exists(output)
            ? JsonParser.parseString(Files.readString(output)).getAsJsonArray() : new JsonArray();
        if (rows.size() > cases.size()) throw new IllegalStateException("Output belongs to another run");
        for (int i = 0; i < rows.size(); i++) {
            if (!rows.get(i).getAsJsonObject().get("id").equals(cases.get(i).getAsJsonObject().get("id")))
                throw new IllegalStateException("Existing output is not a prefix of these cases");
        }
        int completed = rows.size();
        int index = 0;
        EngineConfig config = new EngineConfig(args[0], new Backend.CPU(), null, null, 4096, null, args[3]);
        try (Engine engine = new Engine(config)) {
            engine.initialize();
            for (JsonElement element : cases) {
                if (index++ < completed) continue;
                JsonObject input = element.getAsJsonObject();
                JsonObject row = new JsonObject();
                row.addProperty("id", input.get("id").getAsString());
                long start = System.nanoTime();
                JsonObject prepared = input.getAsJsonObject("prepared");
                try {
                    if (prepared.has("response")) {
                        row.add("actual", prepared.get("response"));
                    } else {
                        ConversationConfig conversationConfig = new ConversationConfig(
                            Contents.Companion.of(prepared.get("system").getAsString()),
                            Collections.emptyList(), Collections.emptyList(),
                            new SamplerConfig(1, 1.0, 0.0, 42), false);
                        try (Conversation conversation = engine.createConversation(conversationConfig)) {
                            AtomicBoolean expired = new AtomicBoolean();
                            ScheduledExecutorService timer = Executors.newSingleThreadScheduledExecutor();
                            try {
                                timer.schedule(() -> { expired.set(true); conversation.cancelProcess(); }, 180, TimeUnit.SECONDS);
                                CompletableFuture<String> completion = new CompletableFuture<>();
                                StringBuffer chunks = new StringBuffer();
                                UnicodeSafeMessages.sendAsync(conversation, prepared.get("text").getAsString(),
                                    new UnicodeSafeMessages.Callback() {
                                        public void onText(String text) {
                                            chunks.append(text);
                                            if (chunks.length() > 16000) {
                                                completion.completeExceptionally(new IllegalStateException("Model output exceeds application limit"));
                                                conversation.cancelProcess();
                                            }
                                        }
                                        public void onDone() { completion.complete(chunks.toString()); }
                                        public void onError(Throwable error) { completion.completeExceptionally(error); }
                                    });
                                String raw = completion.get(180, TimeUnit.SECONDS);
                                if (expired.get()) throw new TimeoutException("Inference exceeded the application timeout");
                                if (raw.length() > 16000) throw new IllegalStateException("Model output exceeds application limit");
                                row.addProperty("raw", raw);
                            } finally { timer.shutdownNow(); }
                        }
                    }
                } catch (Exception error) { row.addProperty("error", error.toString()); }
                row.addProperty("latencyMs", (System.nanoTime() - start) / 1_000_000);
                rows.add(row);
                Path partial = output.resolveSibling(output.getFileName() + ".partial");
                Files.writeString(partial, gson.toJson(rows), StandardCharsets.UTF_8);
                Files.move(partial, output, StandardCopyOption.REPLACE_EXISTING);
                System.out.println(rows.size() + "/" + cases.size() + " " + row.get("id") + " " + row.get("latencyMs") + " ms");
                System.out.flush();
            }
        }
    }
}
