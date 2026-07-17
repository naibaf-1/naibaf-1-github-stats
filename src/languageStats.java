import java.nio.file.*;
import java.util.*;
import java.net.http.*;
import java.net.URI;

public class languageStats {
    public static void main(String[] args) throws Exception {
        // ----------------------------------------------------------------------------------------------
        // Parameters
        // ----------------------------------------------------------------------------------------------
        List<String> owners = List.of("naibaf-1", "CodeJudgeOrg");
        List<String> excludedRepos = List.of("HexPatch", "GNOME-Wallpaper-Collection", "FreeDroidWarn", "naibaf-1", ".github");
        Map<String, String> allowedLanguages = Map.ofEntries(
            Map.entry(".dart", "Dart"),
            Map.entry(".java", "Java"),
            Map.entry(".c", "C"),
            Map.entry(".h", "C"),
            Map.entry(".py", "Python"),
            Map.entry(".sh", "Bash"),
            Map.entry(".txt", "CMake"),
            Map.entry(".cpp", "C++"),
            Map.entry(".hpp", "C++")
        );
        Map<String, List<String>> allowedLanguagesByRepo = Map.ofEntries(
            Map.entry("GroundsBot", List.of(".txt", ".cpp", ".hpp")),
            Map.entry("ROS-TO-CMD", List.of(".sh")),
            Map.entry("Flutter-GlassKit", List.of(".dart")),
            Map.entry("GymTrim", List.of(".java")),
            Map.entry("CodeJudge-Student", List.of(".dart", ".c", ".h")),
            Map.entry("CodeJudge-Teacher", List.of(".dart")),
            Map.entry("CodeJudge-Library", List.of(".dart")),
            Map.entry("CodeJudge-Server", List.of(".py"))
        );
        Map<String, String> languageColors = Map.of(
            "Dart", "#00B4AB",
            "Java", "#b07219",
            "C", "#555555",
            "Python", "#3572A5",
            "Bash", "#89e051",
            "CMake", "#DA3434",
            "C++", "#f34b7d"
        );
        String transmittedChartJSON = """
        {
          "type": "pie",
          "data": {"labels": %s, "datasets": [{"data": %s, "backgroundColor": %s, "borderWidth": 0}]},
          "options": {
            "cutoutPercentage": 64,
            "plugins": {"datalabels": {"display": false}},
            "legend": {"display": true,"position": "left","align": "start","labels": {"fontSize": 24,"fontStyle": "bold","padding": 18}}
          }
        }
        """;
    
        // ----------------------------------------------------------------------------------------------
        // Actual logic
        // ----------------------------------------------------------------------------------------------
        // Variables
        List<String> repoNames = new ArrayList<>();
        List<String> repoUrls = new ArrayList<>();
        Map<String, Integer> extensionCount = new HashMap<>();  
        
        String currentRepo = Path.of("").toAbsolutePath().getFileName().toString();
        System.out.println("Current repo: " + currentRepo);

        // Generate /repos
        Files.createDirectories(Path.of("repos"));

        // Load repositories via GitHub API
        HttpClient http = HttpClient.newHttpClient();

        for (String owner : owners) {
            List<String> urls = List.of(
                "https://api.github.com/users/" + owner + "/repos?per_page=200",
                "https://api.github.com/orgs/" + owner + "/repos?per_page=200"
            );

            for (String url : urls) {
                HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Accept", "application/vnd.github+json")
                    .build();

                String json = http.send(req, HttpResponse.BodyHandlers.ofString()).body();

                // Extract each JSON object cleanly
                String[] objects = json.split("\\},\\{");
                for (String obj : objects) {
                    // Normalize object boundaries
                    obj = obj.replace("[{", "{")
                             .replace("}]", "}")
                             .replace("{", "")
                             .replace("}", "");
                    // Extract repository name
                    String name = null;
                    if (obj.contains("\"name\":")) {
                        try {
                            name = obj.split("\"name\":")[1]
                                      .split(",")[0]
                                      .replace("\"", "")
                                      .trim();
                        } catch (Exception ignored) {}
                    }
                    // Extract ssh_url
                    String ssh = null;
                    if (obj.contains("\"ssh_url\":")) {
                        try {
                            ssh = obj.split("\"ssh_url\":")[1]
                                     .split(",")[0]
                                     .replace("\"", "")
                                     .trim();
                        } catch (Exception ignored) {}
                    }
                    // Skip invalid entries
                    if (name == null || ssh == null || name.isBlank() || ssh.isBlank() || ssh.equals("null")) {
                        continue;
                    }
                    // Remember each repository name and its url
                    repoNames.add(name);
                    repoUrls.add(ssh);
                }
            }
        }

        // Move through each repository and count the occurrences of the file extensions
        int repoCount = repoNames.size();
        for (int i = 0; i < repoCount; i++) {
            String repoName = repoNames.get(i);

            // Skip the repo that contains this script 
            if (repoName.equals(currentRepo)) {
                continue;
            }
            // Skip excluded repositories
            if (excludedRepos.contains(repoName)) {
                continue;
            }

            // Get the correct url and path based on a repository name
            String url = repoUrls.get(i).replace("git@github.com:", "https://github.com/");
            String path = "repos/" + repoName;

            // Clone the repository
            Process clone = new ProcessBuilder("git", "clone", "--depth", "1", "--recursive", url, path).start();
            clone.waitFor();

            // Skip if the clone failed
            if (!Files.exists(Path.of(path))) {
                System.out.println("Skipping " + repoName + " (clone failed)");
                continue;
            }

            // Count the files with the same extension for each repository
            try (var stream = Files.walk(Path.of(path))) {
                stream.filter(Files::isRegularFile).forEach(f -> {
                    String extension = getExtension(f.toString());
                    
                    // Get allowed languages by repo
                    List<String> allowedLanguagesForThisRepo = allowedLanguagesByRepo.get(repoName);
                    // Return if allowedLanguagesForThisRepo is null
                    if(allowedLanguagesForThisRepo == null) {
                      System.out.println("Skipping " + repoName + " (allowedLanguagesForThisRepo == null)");
                      return;
                    }
                    
                    // Skip forbidden languages
                    if(!allowedLanguagesForThisRepo.contains(extension)) {
                      return;
                    }
                    // Handle allowed languages => Find the extension key in the Map => Add 1 to the value
                    extensionCount.merge(extension, 1, Integer::sum);
                });
            }
        }

        // Get a Map with the name of the languages instead of the extensions
        Map<String, Integer> languageCount = new LinkedHashMap<>();
        for (var extension : extensionCount.entrySet()) {
            String language = allowedLanguages.get(extension.getKey());
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
        String labelsJSON = languageCount.keySet().stream().map(s -> "\"" + s + "\"")
                .reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");

        // Convert values to a JSON array
        String valuesJSON = languageCount.values().stream().map(Object::toString)
                .reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");

        // Convert colors to a JSON array
        String colorsJSON = languageCount.keySet().stream().map(lang -> "\"" + languageColors.get(lang) + "\"")
                .reduce((a, b) -> a + "," + b).map(s -> "[" + s + "]").orElse("[]");

        // Create a JSON String for the chart and remove whitespace and spaces from it
        String compactDataJson = transmittedChartJSON.formatted(labelsJSON, valuesJSON, colorsJSON)
                .replace("\n", "").replace("\r", "").replace(" ", "");

        // Create the request for the chart using the JSON String
        HttpRequest req = HttpRequest.newBuilder()
            .uri(URI.create("https://quickchart.io/chart"))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("{\"chart\":" + compactDataJson + "}"))
            .build();

        // Send the request and receive the image as a byte[]
        HttpClient client = HttpClient.newHttpClient();
        byte[] pngImage = client.send(req, HttpResponse.BodyHandlers.ofByteArray()).body();

        // Save the returned image as a png
        Files.write(Path.of("languageStats.png"), pngImage);
    }

    // Get the extension of a file
    private static String getExtension(String file) {
        int i = file.lastIndexOf('.');
        return i == -1 ? "" : file.substring(i).toLowerCase();
    }
}
