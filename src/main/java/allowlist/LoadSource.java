package allowlist;

public final class LoadSource {
    private final String filePath; // non null if file
    private final String resourceName; // non null if classpath resource

    private LoadSource(String filePath, String resourceName) {
        this.filePath = filePath;
        this.resourceName = resourceName;
    }

    public static LoadSource file(String path) {
        return new LoadSource(path, null);
    }

    public static LoadSource resource(String name) {
        return new LoadSource(null, name);
    }

    public boolean isFile() {
        return filePath != null;
    }

    public String filePath() {
        return filePath;
    }

    public String resourceName() {
        return resourceName;
    }
}
