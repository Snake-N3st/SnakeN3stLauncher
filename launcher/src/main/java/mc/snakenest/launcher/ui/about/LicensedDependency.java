package mc.snakenest.launcher.ui.about;

/**
 * One row of {@code THIRD-PARTY-NOTICES.md}'s dependency table, kept in sync with it by hand -
 * {@code licenseResource} is a file name under {@code src/main/resources/licenses/}.
 */
record LicensedDependency(String name, String version, String license, String licenseResource) {

    @Override
    public String toString() {
        return name + " " + version + " (" + license + ")";
    }
}
