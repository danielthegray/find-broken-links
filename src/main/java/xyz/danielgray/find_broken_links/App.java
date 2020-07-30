package xyz.danielgray.find_broken_links;

import picocli.CommandLine;

public class App {
    public static void main(String[] args) {
        if (System.getProperty("log4j.configurationFile") == null) {
            System.setProperty("log4j.configurationFile", "log4j2.properties");
        }
        int exitCode = new CommandLine(new CrawlExecutor()).execute(args);
        System.exit(exitCode);
    }
}
