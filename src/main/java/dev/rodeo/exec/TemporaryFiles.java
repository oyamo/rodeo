package dev.rodeo.exec;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.springframework.util.FileSystemUtils;

final class TemporaryFiles implements AutoCloseable {

    private final List<Path> paths = new ArrayList<>();

    Path directory(String prefix) throws IOException {
        return track(Files.createTempDirectory(prefix));
    }

    Path file(String prefix) throws IOException {
        return track(Files.createTempFile(prefix, ""));
    }

    @Override
    public void close() throws IOException {
        for (Path path : paths) {
            FileSystemUtils.deleteRecursively(path);
        }
    }

    private Path track(Path path) {
        paths.add(path);
        return path;
    }
}
