package org.ome.converter.dao.api;

import org.ome.converter.dao.entity.UserSettingsEntity;

public interface SettingsRepository {
    UserSettingsEntity loadSettings();
    void saveSettings(UserSettingsEntity settings);
}
