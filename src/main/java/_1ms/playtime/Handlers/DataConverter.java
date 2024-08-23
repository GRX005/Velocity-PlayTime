package _1ms.playtime.Handlers;

import _1ms.playtime.Main;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

public class DataConverter {
    private final String file = "plugins" + File.separator + "velocityplaytime" + File.separator + "playtimes.yml";
    private final Main main;
    private final ConfigHandler configHandler;
    private long start;

    public DataConverter(Main main, ConfigHandler configHandler) {
        this.main = main;
        this.configHandler = configHandler;
    }

    public void checkConfig() {
        start = System.currentTimeMillis();
        try {
            BufferedReader reader = new BufferedReader(new FileReader(file));

            Pattern pattern = Pattern.compile(" {2}[a-f0-9-]+:([^:]+):");
            String line;
            int i = 0;
            while ((line = reader.readLine()) != null) {
                i++;
                if(i == 5) {
                    configHandler.modifyMainConfig("isDataFileUpToDate", true);
                    break;
                }
                Matcher matcher = pattern.matcher(line);
                if (matcher.find()) {
                    main.getLogger().info("Outdated data file found, converting to new format...");
                    ConvertData();
                    break;
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void ConvertData() {
        try {
            // Read all lines from the file
            Path path = Paths.get(file);
            StringBuilder content = new StringBuilder();
            Pattern pattern = Pattern.compile(" {2}[a-f0-9-]+:([^:]+):"); // Updated regex to ensure there's a player name

            // Read the file line by line and process it
            try (Stream<String> lines = Files.lines(path)) {
                lines.forEach(line -> {
                    Matcher matcher = pattern.matcher(line);
                    if (matcher.find()) {
                        // Extract player name and reformat the line
                        String playerName = matcher.group(1);
                        line = "  " + playerName + ":";
                    }
                    content.append(line).append(System.lineSeparator());
                });
            }

            // Write the processed content back to the same file
            Files.write(path, content.toString().getBytes());
            main.getLogger().info("Your data file has been converted, took: {} ms", System.currentTimeMillis() - start);

        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

}
