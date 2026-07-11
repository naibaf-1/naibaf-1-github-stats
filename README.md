# naibaf-1-github-stats
Stats for my github profile.

## Parameters
|Value:|Function:|
|--------|-----------|
|`owners`|Included users/organisations|
|`excludedRepos`|Excluded repositories|
|`allowedLanguages`|All allowed languages|
|`allowedLanguagesByRepo`|Allowed languages for each repo|
|`languageColors`|Color per each language|
|`transmittedChartJSON`|Template for the request JSON|

*The parameters are located at the top of `languageStats.java` und `/src`*

## Troubleshooting
#### Skipping xy (clone failed)
An error occured while cloning the repository. Therefore make sure the repository is accessible. For further understanding take a look at **line 148**.

#### Skipping xy (allowedLanguagesForThisRepo == null)
This error is originated at **line 161**. Usually it indicates, that you forgot to provide a list of allowed languages for the given repository. Therefore take a look at the `allowedLanguagesByRepo` parameter and check whether a language is allowed or not.

## Output:
- **Skipping xy (clone failed):** See line 93 of `languageStats.java`
- **Current repo: xy:** See line 43 of `languageStats.java`

## How it works
The small script works as follows:
First of all it first gets all repositories of each owner and clones them to `/repo`. Then it moves through each repository and counts the amount of files matching the allowed file extension for that certain repository. After this it goes on by creating a JSON, which matches the required response schema of Quickchart, by using the names of the languages instead of the file extensions. Finally it requests the graph and saves it as `languageStats.png`.
