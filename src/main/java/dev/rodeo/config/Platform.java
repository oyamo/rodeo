package dev.rodeo.config;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

public final class Platform {

    public enum Os {
        WINDOWS,
        MACOS,
        LINUX
    }

    private static final List<String> UNIX_DIRECTORIES = List.of(
            "/opt/homebrew/bin", "/usr/local/sbin", "/usr/local/bin", "/usr/sbin", "/usr/bin", "/sbin", "/bin");

    private static final List<String> WINDOWS_DIRECTORIES = List.of(
            "Windows\\System32",
            "Windows\\System32\\WindowsPowerShell\\v1.0",
            "Program Files\\PowerShell\\7",
            "Program Files\\Git\\cmd",
            "Program Files\\Docker\\Docker\\resources\\bin");

    private Platform() {
    }

    public static Os os() {
        String name = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (name.startsWith("windows")) {
            return Os.WINDOWS;
        }
        if (name.startsWith("mac")) {
            return Os.MACOS;
        }
        return Os.LINUX;
    }

    public static Path home() {
        return Path.of(System.getProperty("user.home"));
    }

    public static Path systemDrive() {
        return home().getRoot();
    }

    public static String systemRoot() {
        return systemDrive().resolve("Windows").toString();
    }

    public static String homeDirectory() {
        return home().toString();
    }

    public static Map<String, String> hostEnv() {
        Map<String, String> values = new LinkedHashMap<>();
        if (os() == Os.WINDOWS) {
            values.put("SystemRoot", systemRoot());
        }
        return values;
    }

    public static String path() {
        return searchDirectories().stream()
                .map(Path::toString)
                .reduce((left, right) -> left + File.pathSeparator + right)
                .orElse("");
    }

    public static String shell() {
        if (os() == Os.WINDOWS) {
            return find("pwsh").or(() -> find("powershell")).orElse("powershell");
        }
        Path posixShell = Path.of("/bin/sh");
        return Files.isRegularFile(posixShell) ? posixShell.toString() : find("sh").orElse("sh");
    }

    public static String shellFlag() {
        return os() == Os.WINDOWS ? "-Command" : "-c";
    }

    public static String tool(String name) {
        return find(name).orElse(name);
    }

    static Optional<String> find(String name) {
        List<String> candidates = os() == Os.WINDOWS ? List.of(name + ".exe", name) : List.of(name);
        for (Path directory : searchDirectories()) {
            for (String candidate : candidates) {
                Path file = directory.resolve(candidate);
                if (Files.isRegularFile(file)) {
                    return Optional.of(file.toString());
                }
            }
        }
        return Optional.empty();
    }

    static List<Path> searchDirectories() {
        List<String> names = os() == Os.WINDOWS
                ? WINDOWS_DIRECTORIES.stream().map(directory -> systemDrive().resolve(directory).toString()).toList()
                : UNIX_DIRECTORIES;
        return names.stream()
                .map(Path::of)
                .filter(Files::isDirectory)
                .toList();
    }
}
