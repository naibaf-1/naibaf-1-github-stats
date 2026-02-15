import java.nio.file.*;
import java.util.*;
import java.net.http.*;
import java.net.URI;

public class languageStats {
    public static void main(String[] args) throws Exception {
        // --------------------------------
        // Variables, Lists, &c.
        // --------------------------------
        List<String> repoNames = new ArrayList<>();
        List<String> repoUrls = new ArrayList<>();
        Map<String, Integer> extensionCount = new HashMap<>();

        // Organisations and users to scan
        List<String> owners = List.of("naibaf-1", "CodeJudgeOrg");

        // Repos to exclude
        List<String> excludedRepos = List.of("HexPatch", "GNOME-Wallpaper-Collection");

        // Map of languages, which will be displayed
        Map<String, String> displayedLanguages = Map.ofEntries(
            Map.entry(".dart", "Dart"),
            Map.entry(".java", "Java"),
            Map.entry(".c", "C"),
            Map.entry(".h", "C")
        );

        // Colors of the languages
        Map<String, String> languageColor = Map.of(
            "Dart", "#00B4AB",
            "Java", "#b07219",
            "C", "#555555"
        );

        // JSON structure which will be transmitted
        String chartJSON = """
        {
            "type": "pie",
            "data": {
                "labels": %s,
                "datasets": [{
                    "data": %s,
                    "backgroundColor": %s,
                    "borderWidth": 0
                }]
            },
            "options": {
                "cutoutPercentage": 64,
                "plugins": {
                    "datalabels": {
                        "display": false
                    }
                },
                "legend": {
                    "display": true,
                    "position": "left",
                    "align": "start",
                    "labels": {
                        "fontSize": 24,
                        "fontStyle": "bold",
                        "padding": 18
                    }
                }
            }
        }
        """;

        // --------------------------------
        // Actual logic
        // --------------------------------
        String currentRepo = Path.of("").toAbsolutePath().getFileName().toString();
        System.out.println("Current repo: " + currentRepo);

        // Generate /repos
        Files.createDirectories(Path.of("repos"));

        // Fill the two lists with the name and the url of a repository for all owners
        for (String owner : owners) {
            // Request all repositories of an owner
            Process process = new ProcessBuilder("gh", "repo", "list", owner, "--json", "name,sshUrl", "--limit", "200")
                    .redirectErrorStream(true)
                    .start();
            String json = new String(process.getInputStream().readAllBytes());
            process.waitFor();

            // Fill the two lists with the name and the url of an repository
            for (String entry : json.split("\\{")) {
                if (entry.contains("sshUrl")) {
                    String name = entry.split("\"name\":\"")[1].split("\"")[0];
                    String url = entry.split("\"sshUrl\":\"")[1].split("\"")[0];
                    repoNames.add(name);
                    repoUrls.add(url);
                }
            }
        }

        // Move through each repository and count the occurrences of the file extensions
        int repoCount = repoNames.size();

        for (int i = 0; i < repoCount; i++) {
            String name = repoNames.get(i);

            // Skip the repo that contains this script 
            if (name.equals(currentRepo)) {
                continue;
            }

            // Skip excluded repos
            if (excludedRepos.contains(name)) {
                continue;
            }

            String url = repoUrls.get(i).replace("git@github.com:", "https://github.com/");
            String path = "repos/" + name;

            // Clone the repository
            Process clone = new ProcessBuilder("git", "clone", "--depth", "1", "--recursive", url, path).start();
            clone.waitFor();

            // Skip if the clone failed
            if (!Files.exists(Path.of(path))) {
                System.out.println("Skipping " + name + " (clone failed)");
                continue;
            }

            // Count the files with the same extension for each repository
            try (var stream = Files.walk(Path.of(path))) {
                stream.filter(Files::isRegularFile).forEach(f -> {
                    String extension = getExtension(f.toString());

                    // Only count Java, C, Dart
                    if (!extension.equals(".java") && !extension.equals(".dart") && !extension.equals(".c") && !extension.equals(".h")) {
                        return; // ignore everything else
                    }

                    // Find the extension key in the Map => Add 1 to the value
                    extensionCount.merge(extension, 1, Integer::sum);
                });
            }
        }

        // Get a Map with the name of the languages instead of the extensions
        Map<String, Integer> languageCount = new LinkedHashMap<>();
        for (var extension : extensionCount.entrySet()) {
            String language = displayedLanguages.get(extension.getKey());
            if (language != null) {
                languageCount.merge(language, extension.getValue(), Integer::sum);
            }
        }

        // Convert languageCount to a JSON String
        StringBuilder jsonOut = new StringBuilder("{");
        boolean first = true;
        for (var extension : languageCount.entrySet()) {
            if (!first) jsonOut.append(",");
            first = false;
            jsonOut.append("\"").append(extension.getKey()).append("\":").append(extension.getValue());
        }
        jsonOut.append("}");

        // Save the JSON String into a file
        Files.writeString(Path.of("languageStats.json"), jsonOut.toString());

        // Convert labels to a JSON array
        String labelsJSON = languageCount.keySet().stream().map(s -> "\"" + s + "\"").reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");

        // Convert values to a JSON array
        String valuesJSON = languageCount.values().stream().map(Object::toString).reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");

        // Convert colors to a JSON array
        String colorsJSON = languageCount.keySet().stream().map(lang -> "\"" + languageColor.get(lang) + "\"").reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");

        // Create a JSON String for the chart and remove whitespace and spaces from it
        String compactDataJson = chartJSON.formatted(labelsJSON, valuesJSON, colorsJSON).replace("\n", "").replace("\r", "").replace(" ", "");

        // Create the request for the chart using the JSON String
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://quickchart.io/chart"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"chart\":" + compactDataJson + "}"))
            .build();

        // Send the request and receive the image as a byte[]
        HttpClient client = HttpClient.newHttpClient();
        byte[] png = client.send(req, HttpResponse.BodyHandlers.ofByteArray()).body();

        // Save the returned image as a png
        Files.write(Path.of("languageStats.png"), png);
    }

    // Get the extension of a file
    private static String getExtension(String file) {
        int i = file.lastIndexOf('.');
        return i == -1 ? "" : file.substring(i).toLowerCase();
    }
}
