package dev.rodeo.exec;

import java.util.List;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;

public record RepoConfig(List<String> branches, List<String> commands) {

    public static RepoConfig parse(String toml) {
        TomlParseResult result = Toml.parse(toml);
        if (result.hasErrors()) {
            throw new IllegalArgumentException("Invalid rodeo.toml: " + result.errors().getFirst().getMessage());
        }
        return new RepoConfig(strings(result, "branches"), strings(result, "commands"));
    }

    private static List<String> strings(TomlParseResult result, String key) {
        TomlArray array = result.getArray(key);
        return array == null ? List.of() : array.toList().stream().map(String.class::cast).toList();
    }
}
