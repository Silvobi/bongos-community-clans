package pl.bongo.clans;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import pl.bongo.clans.ClanData.State;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Function;

/** All mutations run on the server thread. Persist a validated copy before making it visible. */
public final class ClanStore {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private final Path file;
    private State state;
    public ClanStore(Path file) throws IOException {
        this.file = file;
        if (Files.exists(file)) {
            try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                state = GSON.fromJson(reader, State.class);
                if (state == null) throw new IOException("Empty clan data file");
                ClanData.validate(state);
            } catch (RuntimeException e) { throw new IOException("Invalid clan data; original file preserved: " + file, e); }
        } else state = new State();
    }
    public State state() { return state; }
    public <T> T update(Function<State, T> change) {
        State next = GSON.fromJson(GSON.toJson(state), State.class);
        T result = change.apply(next);
        ClanData.validate(next);
        try {
            Files.createDirectories(file.getParent());
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            try (var stream = new FileOutputStream(temp.toFile())) {
                stream.write(GSON.toJson(next).getBytes(StandardCharsets.UTF_8));
                stream.getFD().sync();
            }
            if (Files.exists(file)) Files.copy(file, file.resolveSibling(file.getFileName() + ".bak"), StandardCopyOption.REPLACE_EXISTING);
            try { Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING); }
            catch (AtomicMoveNotSupportedException e) { Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING); }
        } catch (IOException e) { throw new java.io.UncheckedIOException("Could not save clan data", e); }
        state = next;
        return result;
    }
}
