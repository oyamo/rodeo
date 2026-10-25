package dev.rodeo.pipeline;

import dev.rodeo.config.ExecProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.springframework.stereotype.Component;
import org.tomlj.Toml;
import org.tomlj.TomlArray;
import org.tomlj.TomlParseResult;
import org.tomlj.TomlTable;

@Component
public class PipelineParser {

    private static final Pattern DURATION = Pattern.compile("(\\d+)([smh])");
    private static final Pattern NAME = Pattern.compile("[A-Za-z0-9_-]+");
    private static final String DEFAULT_TIMEOUT = "10m";
    private static final String LEGACY_PIPELINE = "default";
    private static final int MAX_RETRIES = 5;
    private static final int MAX_LABEL_LENGTH = 60;
    private static final Set<String> BRANCH_KEYS = Set.of("on", "branches");
    private static final Set<String> PIPELINE_OPTIONS = Set.of("on", "env");

    private final Set<String> allowedSecrets;

    public PipelineParser(ExecProperties properties) {
        this.allowedSecrets = Set.copyOf(properties.allowedSecrets());
    }

    public PipelineFile parse(String toml) {
        TomlParseResult result = Toml.parse(toml);
        if (result.hasErrors()) {
            throw new PipelineException("invalid rodeo.toml: " + result.errors().getFirst().getMessage());
        }

        List<String> defaultBranches = branches(result);
        if (result.contains("commands")) {
            return legacy(result, defaultBranches);
        }

        List<PipelineSpec> pipelines = new ArrayList<>();
        for (String key : result.keySet()) {
            if (BRANCH_KEYS.contains(key)) {
                continue;
            }
            if (!result.isTable(key)) {
                throw new PipelineException("unknown top-level key '" + key + "'; pipelines are tables such as [frontend]");
            }
            pipelines.add(pipeline(key, result.getTable(key), defaultBranches));
        }
        if (pipelines.isEmpty()) {
            throw new PipelineException("rodeo.toml defines no pipelines");
        }

        PipelineFile file = new PipelineFile(pipelines);
        validate(file);
        return file;
    }

    private static List<String> branches(TomlTable table) {
        return table.contains("on") ? strings(table, "on") : strings(table, "branches");
    }

    private PipelineFile legacy(TomlParseResult result, List<String> branches) {
        StageSpec stage = new StageSpec(
                LEGACY_PIPELINE,
                "default",
                "default",
                List.of(),
                Condition.SUCCESS,
                commands(result.get("commands"), "commands"),
                List.of(),
                Map.of(),
                List.of(),
                null,
                timeout(DEFAULT_TIMEOUT, LEGACY_PIPELINE),
                0,
                false);
        return new PipelineFile(List.of(new PipelineSpec(LEGACY_PIPELINE, branches, List.of(stage))));
    }

    private PipelineSpec pipeline(String name, TomlTable table, List<String> defaultBranches) {
        requireName(name, "pipeline");
        List<String> branches = table.contains("on") ? strings(table, "on") : defaultBranches;
        Map<String, String> env = env(table);

        List<StageSpec> stages = new ArrayList<>();
        String previous = null;
        for (String key : table.keySet()) {
            if (PIPELINE_OPTIONS.contains(key)) {
                continue;
            }
            StageSpec stage = stage(name, key, table.get(key), env, previous);
            stages.add(stage);
            previous = stage.id();
        }
        if (stages.isEmpty()) {
            throw new PipelineException("pipeline " + name + " has no stages");
        }
        return new PipelineSpec(name, branches, stages);
    }

    private StageSpec stage(String pipeline, String name, Object value, Map<String, String> pipelineEnv, String previous) {
        requireName(name, "stage in pipeline " + pipeline);
        String where = pipeline + "/" + name;

        if (value instanceof String || value instanceof TomlArray) {
            return new StageSpec(
                    pipeline,
                    name,
                    name,
                    previous == null ? List.of() : List.of(previous),
                    Condition.SUCCESS,
                    commands(value, where),
                    List.of(),
                    Map.copyOf(pipelineEnv),
                    List.of(),
                    null,
                    timeout(DEFAULT_TIMEOUT, where),
                    0,
                    false);
        }
        if (!(value instanceof TomlTable options)) {
            throw new PipelineException(where + " must be a command, a list of commands, or a table");
        }

        Object run = options.get("run");
        if (run == null) {
            throw new PipelineException(where + " has no run");
        }

        List<String> needs = options.contains("needs")
                ? strings(options, "needs").stream().map(need -> qualify(need, pipeline)).toList()
                : previous == null ? List.of() : List.of(previous);

        Map<String, String> env = new LinkedHashMap<>(pipelineEnv);
        env.putAll(env(options));

        List<String> secrets = strings(options, "secrets");
        for (String secret : secrets) {
            if (!allowedSecrets.contains(secret)) {
                throw new PipelineException(where + " requests secret " + secret + ", which this server does not allow");
            }
        }

        String dir = optional(options, "dir", null);
        if (dir != null) {
            validateDir(where, dir);
        }

        long retries = optionalLong(options, "retries", 0);
        if (retries < 0 || retries > MAX_RETRIES) {
            throw new PipelineException(where + " retries must be between 0 and " + MAX_RETRIES);
        }

        return new StageSpec(
                pipeline,
                name,
                label(options, name, where),
                needs,
                Condition.parse(optional(options, "when", "success")),
                commands(run, where),
                options.contains("on_fail") ? commands(options.get("on_fail"), where) : List.of(),
                Map.copyOf(env),
                secrets,
                dir,
                timeout(optional(options, "timeout", DEFAULT_TIMEOUT), where),
                (int) retries,
                optionalBoolean(options, "optional", false));
    }

    private static String qualify(String need, String pipeline) {
        return need.contains(".") ? need.replace('.', '/') : pipeline + "/" + need;
    }

    private static void validate(PipelineFile file) {
        Set<String> pipelineNames = new HashSet<>();
        Map<String, StageSpec> stages = new LinkedHashMap<>();
        for (PipelineSpec pipeline : file.pipelines()) {
            if (!pipelineNames.add(pipeline.name())) {
                throw new PipelineException("duplicate pipeline " + pipeline.name());
            }
            for (StageSpec stage : pipeline.stages()) {
                if (stages.putIfAbsent(stage.id(), stage) != null) {
                    throw new PipelineException("duplicate stage " + stage.id());
                }
            }
        }
        for (StageSpec stage : stages.values()) {
            for (String need : stage.needs()) {
                if (!stages.containsKey(need)) {
                    throw new PipelineException(stage.id() + " needs unknown stage " + need);
                }
            }
        }
        checkAcyclic(stages);
    }

    private static void checkAcyclic(Map<String, StageSpec> stages) {
        Map<String, Integer> pendingNeeds = new HashMap<>();
        Map<String, List<String>> dependents = new HashMap<>();
        for (StageSpec stage : stages.values()) {
            pendingNeeds.put(stage.id(), stage.needs().size());
            for (String need : stage.needs()) {
                dependents.computeIfAbsent(need, key -> new ArrayList<>()).add(stage.id());
            }
        }

        Queue<String> ready = new LinkedList<>();
        pendingNeeds.forEach((id, count) -> {
            if (count == 0) {
                ready.add(id);
            }
        });

        int resolved = 0;
        while (!ready.isEmpty()) {
            String id = ready.remove();
            resolved++;
            for (String dependent : dependents.getOrDefault(id, List.of())) {
                if (pendingNeeds.merge(dependent, -1, Integer::sum) == 0) {
                    ready.add(dependent);
                }
            }
        }

        if (resolved < stages.size()) {
            List<String> cyclic = pendingNeeds.entrySet().stream()
                    .filter(entry -> entry.getValue() > 0)
                    .map(Map.Entry::getKey)
                    .sorted()
                    .toList();
            throw new PipelineException("dependency cycle involving " + String.join(", ", cyclic));
        }
    }

    private static void requireName(String name, String context) {
        if (!NAME.matcher(name).matches()) {
            throw new PipelineException(context + " name '" + name + "' may only contain letters, digits, '-' and '_'");
        }
    }

    private static void validateDir(String where, String dir) {
        Path path = Path.of(dir);
        if (path.isAbsolute() || path.normalize().startsWith("..")) {
            throw new PipelineException(where + " dir must be a relative path inside the repository");
        }
    }

    private static Duration timeout(String value, String where) {
        Matcher matcher = DURATION.matcher(value);
        if (!matcher.matches()) {
            throw new PipelineException(where + " timeout must look like 30s, 10m or 2h");
        }
        long amount = Long.parseLong(matcher.group(1));
        return switch (matcher.group(2)) {
            case "s" -> Duration.ofSeconds(amount);
            case "m" -> Duration.ofMinutes(amount);
            default -> Duration.ofHours(amount);
        };
    }

    private static List<String> commands(Object value, String where) {
        if (value instanceof String command) {
            return List.of(command);
        }
        if (value instanceof TomlArray array) {
            List<String> commands = new ArrayList<>();
            for (Object item : array.toList()) {
                if (!(item instanceof String command)) {
                    throw new PipelineException(where + " commands must be strings");
                }
                commands.add(command);
            }
            if (commands.isEmpty()) {
                throw new PipelineException(where + " has an empty command list");
            }
            return commands;
        }
        throw new PipelineException(where + " run must be a string or a list of strings");
    }

    private static Map<String, String> env(TomlTable table) {
        TomlTable env = table.getTable("env");
        if (env == null) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : env.keySet()) {
            String value = env.getString(key);
            if (value == null) {
                throw new PipelineException("env." + key + " must be a string");
            }
            values.put(key, value);
        }
        return values;
    }

    private static List<String> strings(TomlTable table, String key) {
        TomlArray array = table.getArray(key);
        if (array == null) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        for (Object item : array.toList()) {
            if (!(item instanceof String value)) {
                throw new PipelineException(key + " must contain only strings");
            }
            values.add(value);
        }
        return values;
    }

    private static String label(TomlTable options, String name, String where) {
        String label = optional(options, "label", name);
        if (label.isBlank() || label.length() > MAX_LABEL_LENGTH) {
            throw new PipelineException(where + " label must be 1 to " + MAX_LABEL_LENGTH + " characters");
        }
        return label;
    }

    private static String optional(TomlTable table, String key, String fallback) {
        if (!table.contains(key)) {
            return fallback;
        }
        String value = table.getString(key);
        if (value == null) {
            throw new PipelineException(key + " must be a string");
        }
        return value;
    }

    private static long optionalLong(TomlTable table, String key, long fallback) {
        if (!table.contains(key)) {
            return fallback;
        }
        Long value = table.getLong(key);
        if (value == null) {
            throw new PipelineException(key + " must be an integer");
        }
        return value;
    }

    private static boolean optionalBoolean(TomlTable table, String key, boolean fallback) {
        if (!table.contains(key)) {
            return fallback;
        }
        Boolean value = table.getBoolean(key);
        if (value == null) {
            throw new PipelineException(key + " must be true or false");
        }
        return value;
    }
}
