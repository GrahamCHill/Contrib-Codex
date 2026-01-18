package dev.grahamhill.service;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.prefs.Preferences;
import dev.grahamhill.ui.MainApp;

public class ConfigManager {

    private final Preferences prefs;
    private final Map<String, String> envConfig = new HashMap<>();

    public ConfigManager() {
        this.prefs = Preferences.userNodeForPackage(MainApp.class);
    }

    public Preferences getPrefs() {
        return prefs;
    }

    public void loadDotenv() {
        try (FileInputStream fis = new FileInputStream(".env")) {
            Properties props = new Properties();
            props.load(fis);
            props.forEach((k, v) -> envConfig.put(k.toString(), v.toString()));
        } catch (IOException e) {
            System.err.println("Could not load .env file: " + e.getMessage());
        }
    }

    public String getEnv(String key, String defaultValue) {
        return envConfig.getOrDefault(key, defaultValue);
    }

    public void saveSetting(String key, String value) {
        if (value != null) {
            prefs.put(key, value);
        }
    }

    public String getSetting(String key, String defaultValue) {
        return prefs.get(key, defaultValue);
    }

    public void saveIntSetting(String key, int value) {
        prefs.putInt(key, value);
    }

    public int getIntSetting(String key, int defaultValue) {
        return prefs.getInt(key, defaultValue);
    }
}
