package org.ome.converter.dao.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.ome.converter.dao.api.SettingsRepository;
import org.ome.converter.dao.entity.UserSettingsEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class JsonFileSettingsRepository implements SettingsRepository {
    private static final Logger log = LoggerFactory.getLogger(JsonFileSettingsRepository.class);

    private final Path storageFile;
    private final ObjectMapper mapper;
    private UserSettingsEntity cachedSettings;

    public JsonFileSettingsRepository() {
        this(Paths.get(System.getProperty("user.home"), ".ome-zarr-converter", "settings.json"));
    }

    public JsonFileSettingsRepository(Path storageFile) {
        this.storageFile = storageFile;
        this.mapper = new ObjectMapper();
        this.mapper.enable(SerializationFeature.INDENT_OUTPUT);
    }

    @Override
    public synchronized UserSettingsEntity loadSettings() {
        if (cachedSettings != null) return cachedSettings;
        try {
            if (Files.exists(storageFile)) {
                cachedSettings = mapper.readValue(storageFile.toFile(), UserSettingsEntity.class);
                return cachedSettings;
            }
        } catch (Exception e) {
            log.error("Failed to load settings file: {}, falling back to defaults", storageFile, e);
        }
        cachedSettings = UserSettingsEntity.defaults();
        return cachedSettings;
    }

    @Override
    public synchronized void saveSettings(UserSettingsEntity settings) {
        if (settings == null) return;
        this.cachedSettings = settings;
        try {
            if (storageFile.getParent() != null) {
                Files.createDirectories(storageFile.getParent());
            }
            mapper.writeValue(storageFile.toFile(), settings);
            log.info("Saved user settings to {}", storageFile.toAbsolutePath());
        } catch (Exception e) {
            log.error("Failed to save user settings to: {}", storageFile, e);
        }
    }
}
