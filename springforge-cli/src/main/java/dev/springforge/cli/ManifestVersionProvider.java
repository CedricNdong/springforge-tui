package dev.springforge.cli;

import picocli.CommandLine.IVersionProvider;

/**
 * Reads the application version from the JAR manifest
 * ({@code Implementation-Version}), injected by Gradle at build time.
 * Falls back to "dev" when running from an IDE or unpackaged classpath.
 */
public final class ManifestVersionProvider implements IVersionProvider {

    private static final String FALLBACK_VERSION = "dev";

    @Override
    public String[] getVersion() {
        String version = MainCommand.class.getPackage()
            .getImplementationVersion();
        return new String[]{
            "springforge " + (version != null ? version : FALLBACK_VERSION)
        };
    }
}
