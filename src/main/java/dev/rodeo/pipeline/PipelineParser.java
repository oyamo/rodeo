package dev.rodeo.pipeline;

import dev.rodeo.config.RodeoProperties;
import dev.rodeo.config.SecretsProperties;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
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
    private static final Pattern IMAGE = Pattern.compile("[A-Za-z0-9][A-Za-z0-9._/:@-]*");

    private static final String DEFAULT_TIMEOUT = "10m";
    private static final String DEFAULT_WHEN = "success";
    private static final String LEGACY_PIPELINE = "default";
    private static final String LEGACY_STAGE = "default";
    private static final int MAX_RETRIES = 5;
    private static final int MAX_LABEL_LENGTH = 60;

    private static final String ON = "on";
    private static final String BRANCHES = "branches";
    private static final String IMAGE_KEY = "image";
    private static final String ENV = "env";
    private static final String COMMANDS = "commands";
    private static final String RUN = "run";
    private static final String NEEDS = "needs";
    private static final String ON_FAIL = "on_fail";
    private static final String TEARDOWN = "teardown";
    private static final String DIR = "dir";
    private static final String ENV_FILE = "env_file";
    private static final String TIMEOUT = "timeout";
    private static final String RETRIES = "retries";
    private static final String HOST = "host";
    private static final String NETWORK = "network";
    private static final String OPTIONAL = "optional";
    private static final String WHEN = "when";
    private static final String LABEL = "label";
    private static final String SECRETS = "secrets";

    private static final Set<String> TOP_LEVEL_OPTIONS = Set.of(ON, BRANCHES, IMAGE_KEY);
    private static final Set<String> PIPELINE_OPTIONS = Set.of(ON, ENV, IMAGE_KEY);
    private static final Set<String> STAGE_OPTIONS = Set.of(
            RUN, LABEL, NEEDS, WHEN, DIR, ENV, SECRETS, TIMEOUT, RETRIES,
            OPTIONAL, ON_FAIL, IMAGE_KEY, NETWORK, HOST, TEARDOWN, ENV_FILE);

    private final Set<String> allowedSecrets;
    private final boolean allowHostExecution;
    private final String defaultImage;

    public PipelineParser(RodeoProperties properties, SecretsProperties secrets) {
        this.allowedSecrets = Set.copyOf(secrets.values().keySet());
        this.allowHostExecution = properties.allowHostExecution();
        this.defaultImage = properties.stages().defaultImage();
    }

    public PipelineFile parse(String toml) {
        TomlParseResult result = Toml.parse(toml);
        if (result.hasErrors()) {
            throw new PipelineException("invalid rodeo.toml: " + result.errors().getFirst().getMessage());
        }
        List<String> branches = branches(result);
        String image = optionalImage(result, defaultImage);
        if (result.contains(COMMANDS)) {
            return legacy(result, branches, image);
        }

        List<PipelineSpec> pipelines = new ArrayList<>();
        for (String key : result.keySet()) {
            if (!TOP_LEVEL_OPTIONS.contains(key)) {
                pipelines.add(pipeline(key, topLevelTable(result, key), branches, image));
            }
        }
        if (pipelines.isEmpty()) {
            throw new PipelineException("rodeo.toml defines no pipelines");
        }
        PipelineFile file = new PipelineFile(pipelines);
        validate(file);
        return file;
    }

    private static TomlTable topLevelTable(TomlParseResult result, String key) {
        if (!result.isTable(key)) {
            throw new PipelineException("unknown top-level key '" + key + "'; pipelines are tables such as [frontend]");
        }
        return result.getTable(key);
    }

    private static List<String> branches(TomlTable table) {
        if (table.contains(ON)) {
            return strings(table, ON);
        }
        return strings(table, BRANCHES);
    }

    private PipelineFile legacy(TomlParseResult result, List<String> branches, String image) {
        requireExecutionTarget(image, false, LEGACY_PIPELINE + "/" + LEGACY_STAGE);
        StageSpec stage = commandStage(LEGACY_PIPELINE, LEGACY_STAGE, List.of(),
                commands(result.get(COMMANDS), COMMANDS), Map.of(), image);
        return new PipelineFile(List.of(new PipelineSpec(LEGACY_PIPELINE, branches, List.of(stage))));
    }

    private PipelineSpec pipeline(String name, TomlTable table, List<String> defaultBranches, String image) {
        requireName(name, "pipeline");
        List<String> branches = defaultBranches;
        if (table.contains(ON)) {
            branches = strings(table, ON);
        }
        Map<String, String> env = env(table);
        String pipelineImage = optionalImage(table, image);

        List<StageSpec> stages = new ArrayList<>();
        String previous = null;
        for (String key : table.keySet()) {
            if (!PIPELINE_OPTIONS.contains(key)) {
                StageSpec stage = stage(name, key, table.get(key), env, previous, pipelineImage);
                stages.add(stage);
                previous = stage.id();
            }
        }
        if (stages.isEmpty()) {
            throw new PipelineException("pipeline " + name + " has no stages");
        }
        return new PipelineSpec(name, branches, stages);
    }

    private StageSpec stage(
            String pipeline,
            String name,
            Object value,
            Map<String, String> pipelineEnv,
            String previous,
            String pipelineImage) {
        requireName(name, "stage in pipeline " + pipeline);
        String where = pipeline + "/" + name;
        List<String> needs = Optional.ofNullable(previous).stream().toList();

        if (value instanceof String || value instanceof TomlArray) {
            requireExecutionTarget(pipelineImage, false, where);
            return commandStage(pipeline, name, needs, commands(value, where), pipelineEnv, pipelineImage);
        }
        if (!(value instanceof TomlTable options)) {
            throw new PipelineException(where + " must be a command, a list of commands, or a table");
        }
        requireKnownOptions(options, where);
        if (!options.contains(RUN)) {
            throw new PipelineException(where + " has no run");
        }

        Map<String, String> env = new LinkedHashMap<>(pipelineEnv);
        env.putAll(env(options));
        List<String> secrets = strings(options, SECRETS);
        secrets.forEach(secret -> requireDefinedSecret(secret, where));
        String dir = relativePath(options, DIR, where);
        String envFile = relativePath(options, ENV_FILE, where);
        int retries = retries(options, where);
        boolean host = optionalBoolean(options, HOST, false);
        if (host && options.contains(IMAGE_KEY)) {
            throw new PipelineException(where + " sets both host and image; choose one");
        }
        String image = null;
        if (!host) {
            image = optionalImage(options, pipelineImage);
        }
        boolean network = optionalBoolean(options, NETWORK, false);
        requireExecutionTarget(image, network, where);

        return new StageSpec(
                pipeline,
                name,
                label(options, name, where),
                needsOf(options, pipeline, needs),
                Condition.parse(optional(options, WHEN, DEFAULT_WHEN)),
                commands(options.get(RUN), where),
                optionalCommands(options, ON_FAIL, where),
                Map.copyOf(env),
                secrets,
                dir,
                timeout(optional(options, TIMEOUT, DEFAULT_TIMEOUT), where),
                retries,
                optionalBoolean(options, OPTIONAL, false),
                image,
                network,
                optionalCommands(options, TEARDOWN, where),
                envFile);
    }

    private StageSpec commandStage(
            String pipeline,
            String name,
            List<String> needs,
            List<String> commands,
            Map<String, String> env,
            String image) {
        return new StageSpec(
                pipeline,
                name,
                name,
                needs,
                Condition.SUCCESS,
                commands,
                List.of(),
                Map.copyOf(env),
                List.of(),
                null,
                timeout(DEFAULT_TIMEOUT, pipeline + "/" + name),
                0,
                false,
                image,
                false,
                List.of(),
                null);
    }

    private static void requireKnownOptions(TomlTable options, String where) {
        for (String option : options.keySet()) {
            if (!STAGE_OPTIONS.contains(option)) {
                throw new PipelineException(where + " has unknown option '" + option
                        + "'; valid options are " + String.join(", ", STAGE_OPTIONS.stream().sorted().toList()));
            }
        }
    }

    private void requireDefinedSecret(String secret, String where) {
        if (!allowedSecrets.contains(secret)) {
            throw new PipelineException(where + " requests secret " + secret
                    + ", which is not defined under [rodeo.secrets] in the server's configuration");
        }
    }

    private static String relativePath(TomlTable options, String key, String where) {
        String value = optional(options, key, null);
        if (value != null) {
            Path path = Path.of(value);
            if (path.isAbsolute() || path.normalize().startsWith("..")) {
                throw new PipelineException(where + " " + key + " must be a relative path inside the repository");
            }
        }
        return value;
    }

    private static int retries(TomlTable options, String where) {
        long retries = optionalLong(options, RETRIES, 0);
        if (retries < 0 || retries > MAX_RETRIES) {
            throw new PipelineException(where + " retries must be between 0 and " + MAX_RETRIES);
        }
        return (int) retries;
    }

    private static List<String> optionalCommands(TomlTable options, String key, String where) {
        if (!options.contains(key)) {
            return List.of();
        }
        return commands(options.get(key), where);
    }

    private void requireExecutionTarget(String image, boolean network, String where) {
        if (image == null && !allowHostExecution) {
            throw new PipelineException(where + " has no image, and host execution is disabled on this server; "
                    + "set image on the stage, its pipeline, or the top level");
        }
        if (image == null && network) {
            throw new PipelineException(where + " sets network, which needs an image");
        }
    }

    private static List<String> needsOf(TomlTable options, String pipeline, List<String> defaults) {
        if (!options.contains(NEEDS)) {
            return defaults;
        }
        return strings(options, NEEDS).stream().map(need -> qualify(need, pipeline)).toList();
    }

    private static String qualify(String need, String pipeline) {
        if (need.contains(".")) {
            return need.replace('.', '/');
        }
        return pipeline + "/" + need;
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
        if (!(value instanceof TomlArray array)) {
            throw new PipelineException(where + " run must be a string or a list of strings");
        }
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

    private static Map<String, String> env(TomlTable table) {
        TomlTable env = table.getTable(ENV);
        if (env == null) {
            return Map.of();
        }
        Map<String, String> values = new LinkedHashMap<>();
        for (String key : env.keySet()) {
            String value = env.getString(key);
            if (value == null) {
                throw new PipelineException(ENV + "." + key + " must be a string");
            }
            values.put(key, value);
        }
        return values;
    }

    private static List<String> strings(TomlTable table, String key) {
        TomlArray array = table.getArray(key);
        if (array == null) {
            return new ArrayList<>();
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
        String label = optional(options, LABEL, name);
        if (label.isBlank() || label.length() > MAX_LABEL_LENGTH) {
            throw new PipelineException(where + " label must be 1 to " + MAX_LABEL_LENGTH + " characters");
        }
        return label;
    }

    private static String optionalImage(TomlTable table, String fallback) {
        if (!table.contains(IMAGE_KEY)) {
            return fallback;
        }
        if (table.isTable(IMAGE_KEY)) {
            throw new PipelineException("'image' is a reserved key; name the pipeline something other than image");
        }
        String image = table.getString(IMAGE_KEY);
        if (image == null || !IMAGE.matcher(image).matches()) {
            throw new PipelineException("image must be a container image reference such as alpine:3.20");
        }
        return image;
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
