/*      This file is part of the Velocity Playtime project.
        Copyright (C) 2024-2025 _1ms

        This program is free software: you can redistribute it and/or modify
        it under the terms of the GNU General Public License as published by
        the Free Software Foundation, either version 3 of the License, or
        (at your option) any later version.

        This program is distributed in the hope that it will be useful,
        but WITHOUT ANY WARRANTY; without even the implied warranty of
        MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
        GNU General Public License for more details.

        You should have received a copy of the GNU General Public License
        along with this program.  If not, see <https://www.gnu.org/licenses/>. */

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

    public void checkForUpdates(){
//        final String latestVersion = new Gson().fromJson(HttpClient.newHttpClient().send(HttpRequest.newBuilder().uri(new URI("https://api.modrinth.com/v2/project/playtime-velocity/version")).GET().build(),
//                HttpResponse.BodyHandlers.ofString()).body(), JsonArray.class).get(0).getAsJsonObject().get("version_number").getAsString();
        final String latestV = getLatest();
        if (latestV != null && !BuildConstants.VERSION.equals(latestV))
            main.getLogger().warn("New version available: {}.", latestV);
        else
            main.getLogger().info("You are using the latest version.");
    }

    String getLatest() {
        try (HttpClient client = HttpClient.newHttpClient()) {
            HttpResponse<String> resp = client.send(HttpRequest.newBuilder().uri(
                    new URI("https://api.modrinth.com/v2/project/playtime-velocity/version")).GET().build(),
                    HttpResponse.BodyHandlers.ofString());
            JsonArray respArr = new Gson().fromJson(resp.body(), JsonArray.class);
            return respArr.get(0).getAsJsonObject().get("version_number").getAsString();
        } catch (URISyntaxException | IOException | InterruptedException e) {
            throw new RuntimeException("Error while searching for updates.",e);
        }
    }
}
