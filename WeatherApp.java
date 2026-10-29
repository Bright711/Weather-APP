
import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class WeatherApp extends JFrame {

    private final JTextField cityField = new JTextField("Nairobi", 18);
    private final JButton searchButton = new JButton("Get Weather");
    private final JLabel cityLabel = new JLabel("Search for a city");
    private final JLabel temperatureLabel = new JLabel("-- °C");
    private final JLabel conditionLabel = new JLabel("Enter a city to begin");
    private final JLabel detailsLabel = new JLabel("Weather details will appear here.");
    private final JTextArea forecastArea = new JTextArea(6, 30);
    private final JLabel statusLabel = new JLabel("Ready");
    private final JComboBox<String> unitBox =
            new JComboBox<>(new String[]{"Celsius (°C)", "Fahrenheit (°F)"});

    private final HttpClient http = HttpClient.newHttpClient();

    private double temperatureC = Double.NaN;
    private double feelsLikeC = Double.NaN;
    private double windKmh = Double.NaN;
    public WeatherApp() {
        setTitle("Weather Information App");
        setSize(520, 600);
        setMinimumSize(new Dimension(430, 520));
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        JPanel main = new JPanel(new BorderLayout(12, 12));
        main.setBorder(new EmptyBorder(18, 18, 18, 18));
        main.setBackground(new Color(239, 246, 255));
        setContentPane(main);

        JLabel heading = new JLabel("WEATHER INFORMATION");
        heading.setFont(new Font("SansSerif", Font.BOLD, 22));
        heading.setForeground(new Color(28, 64, 110));

        JPanel searchPanel = new JPanel(new FlowLayout(FlowLayout.LEFT, 6, 8));
        searchPanel.setOpaque(false);
        searchPanel.add(new JLabel("City:"));
        searchPanel.add(cityField);
        searchPanel.add(searchButton);

        JPanel top = new JPanel(new BorderLayout());

        
        top.setOpaque(false);
        top.add(heading, BorderLayout.NORTH);
        top.add(searchPanel, BorderLayout.SOUTH);
        main.add(top, BorderLayout.NORTH);

        JPanel weatherPanel = new JPanel();
        weatherPanel.setLayout(new BoxLayout(weatherPanel, BoxLayout.Y_AXIS));
        weatherPanel.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(205, 220, 240)),
                new EmptyBorder(20, 16, 20, 16)));
        weatherPanel.setBackground(Color.WHITE);

        cityLabel.setFont(new Font("SansSerif", Font.BOLD, 22));
        cityLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        temperatureLabel.setFont(new Font("SansSerif", Font.BOLD, 46));
        temperatureLabel.setForeground(new Color(28, 100, 180));
        temperatureLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        conditionLabel.setFont(new Font("SansSerif", Font.PLAIN, 17));
        conditionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        detailsLabel.setFont(new Font("SansSerif", Font.PLAIN, 14));
        detailsLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        forecastArea.setEditable(false);
        forecastArea.setFont(new Font("Monospaced", Font.PLAIN, 13));
        forecastArea.setBackground(new Color(248, 250, 253));
        forecastArea.setLineWrap(true);
        forecastArea.setWrapStyleWord(true);

        JLabel forecastTitle = new JLabel("3-Day Forecast");
        forecastTitle.setFont(new Font("SansSerif", Font.BOLD, 17));
        forecastTitle.setAlignmentX(Component.LEFT_ALIGNMENT);

        JPanel unitPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        unitPanel.setOpaque(false);
        unitPanel.add(new JLabel("Temperature unit:"));
        unitPanel.add(unitBox);

        weatherPanel.add(cityLabel);
        weatherPanel.add(Box.createVerticalStrut(12));
        weatherPanel.add(temperatureLabel);
        weatherPanel.add(Box.createVerticalStrut(8));
        weatherPanel.add(conditionLabel);
        weatherPanel.add(Box.createVerticalStrut(12));
        weatherPanel.add(detailsLabel);
        weatherPanel.add(Box.createVerticalStrut(16));
        weatherPanel.add(unitPanel);
        weatherPanel.add(Box.createVerticalStrut(12));
        weatherPanel.add(forecastTitle);
        weatherPanel.add(Box.createVerticalStrut(8));
        weatherPanel.add(new JScrollPane(forecastArea));

        main.add(weatherPanel, BorderLayout.CENTER);

        statusLabel.setBorder(new EmptyBorder(6, 0, 0, 0));
        main.add(statusLabel, BorderLayout.SOUTH);

        searchButton.addActionListener(e -> searchWeather());
        cityField.addActionListener(e -> searchWeather());
        unitBox.addActionListener(e -> updateTemperatureDisplay());

        searchWeather();
    }

    private void searchWeather() {
        String city = cityField.getText().trim();

        if (city.isEmpty()) {
            JOptionPane.showMessageDialog(this,
                    "Please enter a city name.",
                    "Missing City", JOptionPane.WARNING_MESSAGE);
            cityField.requestFocus();
            return;
        }

        searchButton.setEnabled(false);
        statusLabel.setText("Loading weather data...");
        conditionLabel.setText("Please wait...");
        forecastArea.setText("");

        CompletableFuture.runAsync(() -> {
            try {
                String encodedCity = URLEncoder.encode(city, StandardCharsets.UTF_8);
                String geoUrl = "https://geocoding-api.open-meteo.com/v1/search"
                        + "?name=" + encodedCity + "&count=1&language=en&format=json";

                String geoJson = getUrl(geoUrl);
                String results = getObjectSection(geoJson, "results");

                if (results == null || results.equals("[]")) {
                    throw new Exception("City not found. Check the spelling and try again.");
                }

                String locationObject = firstObject(results);
                if (locationObject == null) {
                    throw new Exception("No matching city was found.");
                }

                double latitude = getNumber(locationObject, "latitude");
                double longitude = getNumber(locationObject, "longitude");
                String actualCity = getString(locationObject, "name");
                String country = getString(locationObject, "country");

                String weatherUrl = String.format(Locale.US,
                        "https://api.open-meteo.com/v1/forecast"
                        + "?latitude=%.5f&longitude=%.5f"
                        + "&current=temperature_2m,relative_humidity_2m,"
                        + "apparent_temperature,precipitation,weather_code,wind_speed_10m"
                        + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                        + "&forecast_days=3&timezone=auto",
                        latitude, longitude);

                String weatherJson = getUrl(weatherUrl);

                String current = getObjectSection(weatherJson, "current");
                String daily = getObjectSection(weatherJson, "daily");

                if (current == null || daily == null) {
                    throw new Exception("Weather information was unavailable.");
                }

                double temp = getNumber(current, "temperature_2m");
                double feels = getNumber(current, "apparent_temperature");
                double humidity = getNumber(current, "relative_humidity_2m");
                double wind = getNumber(current, "wind_speed_10m");
                double rain = getNumber(current, "precipitation");
                int code = (int) getNumber(current, "weather_code");

                double[] maxTemps = getNumberArray(daily, "temperature_2m_max");
                double[] minTemps = getNumberArray(daily, "temperature_2m_min");
                double[] codes = getNumberArray(daily, "weather_code");
                String[] dates = getStringArray(daily, "time");

                StringBuilder forecast = new StringBuilder();
                DateTimeFormatter dateFormat = DateTimeFormatter.ofPattern("EEE, dd MMM");

                for (int i = 0; i < dates.length; i++) {
                    String day = LocalDate.parse(dates[i]).format(dateFormat);
                    forecast.append(day).append("\n");

                    if (i < maxTemps.length && i < minTemps.length) {
                        forecast.append(String.format(Locale.US,
                                "  High: %.1f°C   Low: %.1f°C\n",
                                maxTemps[i], minTemps[i]));
                    }

                    if (i < codes.length) {
                        forecast.append("  ")
                                .append(weatherDescription((int) codes[i]))
                                .append("\n");
                    }
                    forecast.append("\n");
                }

                String finalCity = actualCity
                        + (country == null || country.isEmpty() ? "" : ", " + country);
                String finalCondition = weatherDescription(code);
                String finalForecast = forecast.toString();

                SwingUtilities.invokeLater(() -> {
                    temperatureC = temp;
                    feelsLikeC = feels;
                    windKmh = wind;
                    cityLabel.setText(finalCity);
                    conditionLabel.setText(finalCondition);
                    detailsLabel.setText(String.format(Locale.US,
                            "<html><div style='text-align:center;'>"
                            + "Feels like: %.1f°C &nbsp; | &nbsp; Humidity: %.0f%%<br>"
                            + "Wind: %.1f km/h &nbsp; | &nbsp; Rain: %.1f mm"
                            + "</div></html>",
                            feels, humidity, wind, rain));

                    forecastArea.setText(finalForecast);
                    updateTemperatureDisplay();
                    statusLabel.setText("Weather updated successfully.");
                    searchButton.setEnabled(true);
                });

            } catch (Exception ex) {
                SwingUtilities.invokeLater(() -> {
                    statusLabel.setText("Unable to load weather.");
                    conditionLabel.setText("Weather unavailable");
                    searchButton.setEnabled(true);

                    JOptionPane.showMessageDialog(this,
                            ex.getMessage() == null
                                    ? "An unexpected error occurred."
                                    : ex.getMessage(),
                            "Weather Error", JOptionPane.ERROR_MESSAGE);
                });
            }
        });
    }

    private String getUrl(String url) throws Exception {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(url))
                .header("User-Agent", "JavaWeatherApp/1.0")
                .timeout(java.time.Duration.ofSeconds(20))
                .GET()
                .build();

        HttpResponse<String> response = http.send(
                request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() != 200) {
            throw new Exception("The weather service returned error "
                    + response.statusCode() + ". Please try again.");
        }

        return response.body();
    }

    private String getObjectSection(String json, String key) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key) + "\"\\s*:\\s*\\{");
        Matcher matcher = pattern.matcher(json);

        if (!matcher.find()) {
            // The geocoding results field is an array, not an object.
            Pattern arrayPattern = Pattern.compile(
                    "\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[");
            Matcher arrayMatcher = arrayPattern.matcher(json);
            if (arrayMatcher.find()) {
                int start = arrayMatcher.start();
                int open = json.indexOf('[', start);
                int depth = 0;
                boolean quoted = false;
                boolean escaped = false;

                for (int i = open; i < json.length(); i++) {
                    char c = json.charAt(i);

                    if (quoted) {
                        if (escaped) escaped = false;
                        else if (c == '\\') escaped = true;
                        else if (c == '"') quoted = false;
                    } else {
                        if (c == '"') quoted = true;
                        else if (c == '[') depth++;
                        else if (c == ']' && --depth == 0) {
                            return json.substring(open, i + 1);
                        }
                    }
                }
            }
            return null;
        }

        int open = matcher.end() - 1;
        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;

        for (int i = open; i < json.length(); i++) {
            char c = json.charAt(i);

            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else {
                if (c == '"') quoted = true;
                else if (c == '{') depth++;
                else if (c == '}' && --depth == 0) {
                    return json.substring(open, i + 1);
                }
            }
        }
        return null;
    }

    private String firstObject(String array) {
        if (array == null) return null;

        int start = array.indexOf('{');
        if (start < 0) return null;

        int depth = 0;
        boolean quoted = false;
        boolean escaped = false;

        for (int i = start; i < array.length(); i++) {
            char c = array.charAt(i);

            if (quoted) {
                if (escaped) escaped = false;
                else if (c == '\\') escaped = true;
                else if (c == '"') quoted = false;
            } else {
                if (c == '"') quoted = true;
                else if (c == '{') depth++;
                else if (c == '}' && --depth == 0) {
                    return array.substring(start, i + 1);
                }
            }
        }
        return null;
    }

    private double getNumber(String json, String key) throws Exception {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key) + "\"\\s*:\\s*(-?\\d+(?:\\.\\d+)?|null)");
        Matcher matcher = pattern.matcher(json);

        if (matcher.find() && !matcher.group(1).equals("null")) {
            return Double.parseDouble(matcher.group(1));
        }

        throw new Exception("Weather data is missing: " + key);
    }

    private String getString(String json, String key) {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key) + "\"\\s*:\\s*\"((?:\\\\.|[^\"])*)\"");
        Matcher matcher = pattern.matcher(json);

        if (matcher.find()) {
            return matcher.group(1).replace("\\\"", "\"").replace("\\\\", "\\");
        }
        return "";
    }

    private double[] getNumberArray(String json, String key) throws Exception {
        String array = getArray(json, key);
        Matcher matcher = Pattern.compile("-?\\d+(?:\\.\\d+)?").matcher(array);
        java.util.ArrayList<Double> values = new java.util.ArrayList<>();

        while (matcher.find()) {
            values.add(Double.parseDouble(matcher.group()));
        }

        double[] result = new double[values.size()];
        for (int i = 0; i < result.length; i++) result[i] = values.get(i);
        return result;
    }

    private String[] getStringArray(String json, String key) throws Exception {
        String array = getArray(json, key);
        Matcher matcher = Pattern.compile("\"([^\"]+)\"").matcher(array);
        java.util.ArrayList<String> values = new java.util.ArrayList<>();

        while (matcher.find()) values.add(matcher.group(1));

        return values.toArray(new String[0]);
    }

    private String getArray(String json, String key) throws Exception {
        Pattern pattern = Pattern.compile(
                "\"" + Pattern.quote(key) + "\"\\s*:\\s*\\[");
        Matcher matcher = pattern.matcher(json);

        if (!matcher.find()) throw new Exception("Missing forecast data: " + key);

        int start = matcher.end();
        int end = json.indexOf(']', start);
        if (end < 0) throw new Exception("Invalid forecast data: " + key);

        return json.substring(start, end);
    }

    private String weatherDescription(int code) {
        if (code == 0) return "Clear sky";
        if (code == 1) return "Mainly clear";
        if (code == 2) return "Partly cloudy";
        if (code == 3) return "Overcast";
        if (code == 45 || code == 48) return "Foggy";
        if (code >= 51 && code <= 57) return "Drizzle";
        if (code >= 61 && code <= 67) return "Rain";
        if (code >= 71 && code <= 77) return "Snow";
        if (code >= 80 && code <= 82) return "Rain showers";
        if (code == 85 || code == 86) return "Snow showers";
        if (code == 95) return "Thunderstorm";
        if (code == 96 || code == 99) return "Thunderstorm with hail";
        return "Unknown conditions";
    }

    private void updateTemperatureDisplay() {
        if (Double.isNaN(temperatureC)) return;

        boolean fahrenheit = unitBox.getSelectedIndex() == 1;
        double temp = fahrenheit ? temperatureC * 9 / 5 + 32 : temperatureC;
        double feels = fahrenheit ? feelsLikeC * 9 / 5 + 32 : feelsLikeC;
        String unit = fahrenheit ? "°F" : "°C";

        temperatureLabel.setText(String.format(Locale.US, "%.1f %s", temp, unit));

        detailsLabel.setText(String.format(Locale.US,
                "<html><div style='text-align:center;'>"
                + "Feels like: %.1f%s<br>"
                + "Wind: %.1f km/h"
                + "</div></html>",
                feels, unit, windKmh));
    }

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {
            }
            new WeatherApp().setVisible(true);
        });
    }
}
