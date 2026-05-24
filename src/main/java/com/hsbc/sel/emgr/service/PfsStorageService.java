package com.hsbc.sel.emgr.service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import com.hsbc.sel.emgr.config.PfsProperties;

public class PfsStorageService {

    private final PfsProperties properties;

    public PfsStorageService(PfsProperties properties) {
        this.properties = properties;
        initializeDirectories();
    }

    public void initializeDirectories() {
        try {
            Files.createDirectories(properties.getBatchDirPath());
            Files.createDirectories(properties.getHsbcHtmlDirPath());
            Files.createDirectories(properties.getHredHtmlDirPath());
            Files.createDirectories(properties.getTemplateDirPath());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to initialize PFS directories", ex);
        }
    }

    public void clearDirectory(Path dir) {
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> stream = Files.list(dir)) {
            List<Path> targets = stream.collect(Collectors.toList());
            for (Path path : targets) {
                if (Files.isRegularFile(path)) {
                    Files.delete(path);
                }
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to clear directory: " + dir, ex);
        }
    }

    public int importBatchFilesFromZip(byte[] zipBytes, boolean clearFirst) {
        if (clearFirst) {
            clearDirectory(properties.getBatchDirPath());
        }

        int importedCount = 0;

        try (ZipInputStream zis = new ZipInputStream(new java.io.ByteArrayInputStream(zipBytes))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if (entry.isDirectory()) {
                    continue;
                }

                String fileName = new File(entry.getName()).getName();
                if (!fileName.toLowerCase(Locale.ROOT).endsWith(".txt")) {
                    continue;
                }

                Path target = properties.getBatchDirPath().resolve(fileName).normalize();
                if (!target.startsWith(properties.getBatchDirPath())) {
                    throw new IllegalStateException("Invalid zip entry path: " + entry.getName());
                }

                Files.copy(zis, target, StandardCopyOption.REPLACE_EXISTING);
                importedCount++;
                zis.closeEntry();
            }
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to import batch zip", ex);
        }

        return importedCount;
    }

    public List<String> listFileNames(Path dir) {
        if (!Files.exists(dir)) {
            return new ArrayList<String>();
        }
        try (Stream<Path> stream = Files.list(dir)) {
            return stream
                .filter(Files::isRegularFile)
                .map(path -> path.getFileName().toString())
                .sorted()
                .collect(Collectors.toList());
        } catch (IOException ex) {
            throw new IllegalStateException("Failed to list files: " + dir, ex);
        }
    }

}

