package dev.rodeo.model;

public record PushEvent(
        Provider provider,
        String repository,
        String cloneUrl,
        String branch,
        String after,
        String pusher,
        int commitCount) {

    private static final String BRANCH_PREFIX = "refs/heads/";

    static String branchFromRef(String ref) {
        return ref != null && ref.startsWith(BRANCH_PREFIX) ? ref.substring(BRANCH_PREFIX.length()) : ref;
    }
}
