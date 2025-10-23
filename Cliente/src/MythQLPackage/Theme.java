package MythQLPackage;

import java.awt.*;
import java.io.*;
import java.net.URL;
import java.util.*;

public class Theme {
    public Color topPanel, leftPanel, bottomPanel, consoleBg, consoleFg;
    public Map<String, Color> keywordColors = new HashMap<>();

    public static Theme loadFromResource(String resourcePath) throws IOException {
        InputStream input = Theme.class.getResourceAsStream(resourcePath);
        if (input == null)
            throw new FileNotFoundException("No se encontró el tema en: " + resourcePath);

        Properties props = new Properties();
        props.load(input);

        Theme theme = new Theme();
        theme.topPanel = parseColor(props.getProperty("topPanel"));
        theme.leftPanel = parseColor(props.getProperty("leftPanel"));
        theme.bottomPanel = parseColor(props.getProperty("bottomPanel"));
        theme.consoleBg = parseColor(props.getProperty("consoleBg"));
        theme.consoleFg = parseColor(props.getProperty("consoleFg"));

        // Cargar colores de categorías de keywords
        for (String key : props.stringPropertyNames()) {
            if (key.startsWith("keyword")) {
                theme.keywordColors.put(key, parseColor(props.getProperty(key)));
            }
        }

        return theme;
    }

    private static Color parseColor(String hex) {
        if (hex == null) return Color.BLACK;
        try {
            return Color.decode(hex.trim());
        } catch (Exception e) {
            return Color.BLACK;
        }
    }
}