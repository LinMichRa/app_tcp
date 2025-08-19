package org.vinni;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.Properties;

public class Config {
    private static final String CONFIG_FILE = "config.properties";
    private static Properties props = new Properties();

    static {
        try (FileInputStream fis = new FileInputStream(CONFIG_FILE)) {
            props.load(fis);
        } catch (IOException e) {
            System.err.println("Error cargando configuración: " + e.getMessage());
        }
    }

    public static String get(String key) {
        return props.getProperty(key);
    }
}

