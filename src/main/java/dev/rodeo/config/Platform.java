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

    private static final String PATH = "PATH";
    private static final String HOME = "HOME";
    private static final String SYSTEM_ROOT = "SystemRoot";
    private static final String WINDOWS_EXTENSION = ".exe";
    private static final String POSIX_SHELL = "sh";
    private static final String POSIX_SHELL_FLAG = "-c";
    private static final String WINDOWS_SHELL_FLAG = "-Command";
    private static final List<String> WINDOWS_SHELLS = List.of("pwsh", "powershell");
    private static final Os OS = detect(System.getProperty("os.name", ""));

    private static final List<Path> UNIX_DIRECTORIES = List.of(
            Path.of("/opt/homebrew/bin"),
            Path.of("/usr/local/sbin"),
            Path.of("/usr/local/bin"),
            Path.of("/usr/sbin"),
            Path.of("/usr/bin"),
            Path.of("/sbin"),
            Path.of("/bin"));

    private static final List<Path> WINDOWS_DIRECTORIES = List.of(
            Path.of("Windows", "System32"),
            Path.of("Windows", "System32", "WindowsPowerShell", "v1.0"),
            Path.of("Program Files", "PowerShell", "7"),
            Path.of("Program Files", "Git", "cmd"),
            Path.of("Program Files", "Docker", "Docker", "resources", "bin"));

    private Platform() {
    }

    public static Os os() {
        return OS;
    }

    public static Path home() {
        return Path.of(System.getProperty("user.home"));
    }

    public static Map<String, String> environment() {
        Map<String, String> environment = new LinkedHashMap<>();
        environment.put(PATH, path());
        environment.put(HOME, home().toString());
        if (OS == Os.WINDOWS) {
            environment.put(SYSTEM_ROOT, home().getRoot().resolve("Windows").toString());
        }
        return environment;
    }

    public static String path() {
        return String.join(File.pathSeparator, searchDirectories().stream().map(Path::toString).toList());
    }

    public static String shell() {
        if (OS == Os.WINDOWS) {
            return windowsShell();
        }
        return find(POSIX_SHELL).orElse(POSIX_SHELL);
    }

    public static String shellFlag() {
        if (OS == Os.WINDOWS) {
            return WINDOWS_SHELL_FLAG;
        }
        return POSIX_SHELL_FLAG;
    }

    public static String tool(String name) {
        return find(name).orElse(name);
    }

    static Optional<String> find(String name) {
        for (Path directory : searchDirectories()) {
            for (String executable : executableNames(name)) {
                Path file = directory.resolve(executable);
                if (Files.isRegularFile(file)) {
                    return Optional.of(file.toString());
                }
            }
        }
        return Optional.empty();
    }

    static List<Path> searchDirectories() {
        return candidateDirectories().stream().filter(Files::isDirectory).toList();
    }

    private static String windowsShell() {
        return WINDOWS_SHELLS.stream()
                .map(Platform::find)
                .flatMap(Optional::stream)
                .findFirst()
                .orElse(WINDOWS_SHELLS.get(WINDOWS_SHELLS.size() - 1));
    }

    private static List<Path> candidateDirectories() {
        if (OS == Os.WINDOWS) {
            return WINDOWS_DIRECTORIES.stream().map(home().getRoot()::resolve).toList();
        }
        return UNIX_DIRECTORIES;
    }

    private static List<String> executableNames(String name) {
        if (OS == Os.WINDOWS) {
            return List.of(name + WINDOWS_EXTENSION, name);
        }
        return List.of(name);
    }

    private static Os detect(String osName) {
        String name = osName.toLowerCase(Locale.ROOT);
        if (name.startsWith("windows")) {
            return Os.WINDOWS;
        }
        if (name.startsWith("mac")) {
            return Os.MACOS;
        }
        return Os.LINUX;
    }
}
