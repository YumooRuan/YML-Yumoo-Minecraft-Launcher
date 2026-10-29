package dev.yml.core.content;

import java.nio.file.Path;

/** One mod jar that has a newer version available on Modrinth. */
public record ModUpdate(Path oldFile, String title, String currentVersion, String latestVersion,
                        String newFilename, String newUrl, String newSha1, long newSize) {
}
