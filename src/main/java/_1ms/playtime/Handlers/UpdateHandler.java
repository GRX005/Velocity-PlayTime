package _1ms.playtime.Handlers;

import _1ms.BuildConstants;
import _1ms.playtime.Main;
import com.google.gson.Gson;
import com.google.gson.JsonArray;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

public class UpdateHandler {
    private final Main main;

    public UpdateHandler(Main main) {
        this.main = main;
    }

    public void checkForUpdates() throws URISyntaxException, IOException, InterruptedException {
        final String latestVersion = new Gson().fromJson(HttpClient.newHttpClient().send(HttpRequest.newBuilder().uri(new URI("https://api.modrinth.com/v2/project/playtime-velocity/version")).GET().build(),
                HttpResponse.BodyHandlers.ofString()).body(), JsonArray.class).get(0).getAsJsonObject().get("version_number").getAsString();
        if (latestVersion != null && !BuildConstants.VERSION.equals(latestVersion))
            main.getLogger().warn("New version available: {}.", latestVersion);
        else
            main.getLogger().info("You are using the latest version.");
    }
}
