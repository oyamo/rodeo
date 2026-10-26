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
        if (ref == null || !ref.startsWith(BRANCH_PREFIX)) {
            return ref;
        }
        return ref.substring(BRANCH_PREFIX.length());
    }
}
