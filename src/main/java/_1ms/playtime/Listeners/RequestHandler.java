package _1ms.playtime.Listeners;

import _1ms.playtime.Commands.PlaytimeTopCommand;
import _1ms.playtime.Main;
import com.google.common.io.ByteArrayDataInput;
import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.google.gson.Gson;
import com.velocitypowered.api.event.EventTask;
import com.velocitypowered.api.event.Subscribe;
import com.velocitypowered.api.event.connection.PluginMessageEvent;
import com.velocitypowered.api.proxy.ServerConnection;
import com.velocitypowered.api.proxy.server.RegisteredServer;
import com.velocitypowered.api.scheduler.ScheduledTask;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.concurrent.TimeUnit;

@SuppressWarnings({"UnstableApiUsage", "unused"})
public class RequestHandler {

    private final Main main;
    private final PlaytimeTopCommand playtimeTopCommand;
    private final Gson gson = new Gson();
    private ScheduledTask task;
    public RequestHandler(Main main, PlaytimeTopCommand playtimeTopCommand) {
        this.main = main;
        this.playtimeTopCommand = playtimeTopCommand;
    }
    private final List<RegisteredServer> pttServers = new ArrayList<>();

    @Subscribe
    public EventTask onRequest(PluginMessageEvent e) {
        return EventTask.async(() -> {
            if(!(e.getSource() instanceof ServerConnection conn) || e.getIdentifier() != main.MCI)
                return;
            final ByteArrayDataInput in = ByteStreams.newDataInput(e.getData());
            final String req = in.readUTF();
            switch (req) {
                case "rpt" -> main.getProxy().getScheduler().buildTask(main, (task) -> {
                    final HashMap<String, Long> pTempMap = new HashMap<>();
                    conn.getServer().getPlayersConnected().forEach(player -> {
                        final String name = player.getGameProfile().getName();
                        pTempMap.put(name, main.playtimeCache.get(name));
                    });
                    final ByteArrayDataOutput out = ByteStreams.newDataOutput();
                    out.writeUTF("pt");
                    out.writeUTF(gson.toJson(pTempMap));
                    final RegisteredServer server = conn.getServer();
                    server.sendPluginMessage(main.MCI, out.toByteArray());
                }).repeat(1L, TimeUnit.SECONDS).schedule();
                case "rtl" -> {
                    pttServers.add(conn.getServer());
                    if(task == null) {
                        task = main.getProxy().getScheduler().buildTask(main, () -> {
                            final LinkedHashMap<String, Long> topMap = playtimeTopCommand.doSort(null);
                            final String json = gson.toJson(topMap);
                            final ByteArrayDataOutput out = ByteStreams.newDataOutput();
                            out.writeUTF("ptt");
                            out.writeUTF(json);
                            pttServers.forEach(server -> server.sendPluginMessage(main.MCI, out.toByteArray()));
                        }).repeat(1L, TimeUnit.SECONDS).schedule();
                    }
                }
            }
        });
    }
}
