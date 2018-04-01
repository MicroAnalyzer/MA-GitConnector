# MA-GitHubCloner

A git repository connector plug-in for MicroAnalyzer. Used to extract evolution data from git repositories.

## How To Compile Sources

If you checked out the project from GitHub you can build the project with maven using:

```
mvn clean install
```

## Usage
Build the plugin jar and place it in the Java installation's */ext* folder. The return value of the overridden toString() method
corresponds to the parameter identifying the plug-in for MicroAnalyzer. By running the plug-in git repositories found in the local
directory */repositories* are connected to.
