package _1ms.playtime.Listeners;

import _1ms.playtime.Commands.PlaytimeTopCommand;
import _1ms.playtime.Handlers.ConfigHandler;
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

import java.util.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

@SuppressWarnings({"UnstableApiUsage", "unused"})
public class RequestHandler {

    private final Main main;
    private final PlaytimeTopCommand playtimeTopCommand;
    private final ConfigHandler configHandler;
    private final Gson gson = new Gson();
    private ScheduledTask task;
    private final HashMap<RegisteredServer, ScheduledTask> rsTasks = new HashMap<>();
    public RequestHandler(Main main, PlaytimeTopCommand playtimeTopCommand, ConfigHandler configHandler) {
        this.main = main;
        this.playtimeTopCommand = playtimeTopCommand;
        this.configHandler = configHandler;
    }
    private final Set<RegisteredServer> pttServers = new HashSet<>();
    private final Set<RegisteredServer> ptServers = new HashSet<>();

    public void sendRS() {
        @SuppressWarnings("UnstableApiUsage")
        final ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("rs");
        main.getProxy().getAllServers().forEach(server -> main.checkServerStatus(server).thenAccept(status -> {
            if(status){
                rsTasks.put(server, main.getProxy().getScheduler().buildTask(main, () -> {
                    if(!server.getPlayersConnected().isEmpty()) {
                        server.sendPluginMessage(main.MCI, out.toByteArray());
                    }
                }).repeat(1L, TimeUnit.SECONDS).schedule());
            }
        }));
    }

    @Subscribe
    public EventTask onRequest(PluginMessageEvent e) {
        return EventTask.async(() -> {
            if(!(e.getSource() instanceof ServerConnection conn) || e.getIdentifier() != main.MCI)
                return;
            final ByteArrayDataInput in = ByteStreams.newDataInput(e.getData());
            final String req = in.readUTF();

            switch (req) {
                case "cc" -> {
                    final RegisteredServer server22 = conn.getServer();
                    try {
                        rsTasks.get(server22).cancel();
                        rsTasks.remove(server22);
                    } catch (Exception ignored) {}
                }
                case "rpt" -> {
                    final RegisteredServer server22 = conn.getServer();
                    if(configHandler.isPRELOAD_PLACEHOLDERS()) {
                        final ByteArrayDataOutput outA = ByteStreams.newDataOutput();
                        outA.writeUTF("pt");
                        Iterator<Object> iterator = main.getIterator();
                        final HashMap<String, Long> TempCache = new HashMap<>();
                        if (iterator != null) {
                            while (iterator.hasNext()) {
                                String Pname = (String) iterator.next();
                                long Ptime = main.getSavedPt(Pname);
                                TempCache.put(Pname, Ptime);
                                iterator.remove();
                            }
                        }
                        outA.writeUTF(gson.toJson(TempCache));
                        server22.sendPluginMessage(main.MCI, outA.toByteArray());
                    }
                    final ByteArrayDataOutput outA = ByteStreams.newDataOutput();
                    outA.writeUTF("conf");
                    outA.writeBoolean(configHandler.isPRELOAD_PLACEHOLDERS());
                    conn.getServer().sendPluginMessage(main.MCI, outA.toByteArray());

                    if(ptServers.contains(server22))
                        return;
                    ptServers.add(server22);
                    final AtomicReference<RegisteredServer> serverRef = new AtomicReference<>(server22);

                    final AtomicLong currt = new AtomicLong(System.currentTimeMillis());
                    main.getProxy().getScheduler().buildTask(main, (taskIn) -> {
                        final long rCurrt = System.currentTimeMillis();
                        if((rCurrt - currt.get()) > 10000 ) {
                            currt.set(rCurrt);
                            main.checkServerStatus(serverRef.get()).thenAccept(status -> {
                                if(!status) {
                                    taskIn.cancel();
                                    ptServers.remove(serverRef.get());
                                    pttServers.removeIf(asd -> asd.equals(serverRef.get()));
                                }
                            });
                        }
                        final HashMap<String, Long> pTempMap = new HashMap<>();
                        serverRef.get().getPlayersConnected().forEach(player -> {
                            final String name = player.getGameProfile().getName();
                            pTempMap.put(name, main.playtimeCache.get(name));
                        });
                        final ByteArrayDataOutput out = ByteStreams.newDataOutput();
                        out.writeUTF("pt");
                        out.writeUTF(gson.toJson(pTempMap));
                        serverRef.get().sendPluginMessage(main.MCI, out.toByteArray());
                    }).repeat(1L, TimeUnit.SECONDS).schedule();
                }
                case "rtl" -> {
                    pttServers.add(conn.getServer());
                    if(task == null) {
                        task = main.getProxy().getScheduler().buildTask(main, () -> {
                            if(pttServers.isEmpty()) {
                                task.cancel();
                                task = null;
                                return;
                            }
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
