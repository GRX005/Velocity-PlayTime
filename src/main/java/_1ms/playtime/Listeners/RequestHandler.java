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
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;

@SuppressWarnings({"UnstableApiUsage", "unused"})
public class RequestHandler {

    private final Main main;
    private final ConfigHandler configHandler;
    private final Gson gson = new Gson();
    private ScheduledTask task;
    private final HashMap<RegisteredServer, ScheduledTask> rsTasks = new HashMap<>();
    public RequestHandler(Main main, PlaytimeTopCommand playtimeTopCommand, ConfigHandler configHandler) {
        this.main = main;
        this.configHandler = configHandler;
    }
    private final Set<RegisteredServer> pttServers = new HashSet<>();
    private final Set<RegisteredServer> ptServers = new HashSet<>();

    public void sendRS() {
        @SuppressWarnings("UnstableApiUsage")
        final ByteArrayDataOutput out = ByteStreams.newDataOutput();
        out.writeUTF("rs"); //Notify ptlink that the proxy restarted.
        main.getProxy().getAllServers().forEach(server -> main.checkServerStatus(server).thenAccept(status -> {
            if(status){
                AtomicInteger limit = new AtomicInteger(0);
                rsTasks.put(server, main.getProxy().getScheduler().buildTask(main, (rsTask) -> {
                    if(!server.getPlayersConnected().isEmpty()) {
                        if(5 == limit.incrementAndGet()) //Only send it 5 times at max.
                            rsTask.cancel();
                        server.sendPluginMessage(main.MCI, out.toByteArray());
                    }
                }).repeat(1L, TimeUnit.SECONDS).schedule());
            }
        }));
    }

    @Subscribe
    public EventTask onRequest(PluginMessageEvent e) {//TODO document this / rewrite
        return EventTask.async(() -> {
            if (e.getIdentifier() != main.MCI) return;
            e.setResult(PluginMessageEvent.ForwardResult.handled());
            if (!(e.getSource() instanceof ServerConnection conn)) return;
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
                        Iterator<String> iterator = main.getIterator();
                        final HashMap<String, Long> TempCache = new HashMap<>();
                        if (iterator != null) {
                            while (iterator.hasNext()) {
                                String Pname = iterator.next();
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
                    main.getProxy().getScheduler().buildTask(main, taskIn -> {
                        final long rCurrt = System.currentTimeMillis();
                        if ((rCurrt - currt.get()) > 10000) { // Check if the server is still running & doesn't request ptt, every 10 secs.
                            currt.set(rCurrt);
                            main.checkServerStatus(serverRef.get()).thenAccept(status -> {
                                if(!status) //If the server gets stopped before 10 secs of it being started.
                                    pttServers.removeIf(serv -> serv.equals(serverRef.get()));
                                if (!status || pttServers.contains(serverRef.get())) {
                                    taskIn.cancel();
                                    ptServers.remove(serverRef.get());
                                }
                            });
                        }
                        final HashMap<String, Long> pTempMap = new HashMap<>();
                        serverRef.get().getPlayersConnected().forEach(player -> {
                            final String name = player.getUsername();
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
                        final AtomicLong currt = new AtomicLong(System.currentTimeMillis());
                        task = main.getProxy().getScheduler().buildTask(main, () -> {
                            final long rCurrt = System.currentTimeMillis();
                            if ((rCurrt - currt.get()) > 10000) { // Check if the server is still running every 10 secs.
                                currt.set(rCurrt);
                                pttServers.forEach(server -> main.checkServerStatus(server).thenAccept(status -> {
                                    if(!status)
                                        pttServers.remove(server);
                                }));
                            }
                            if(pttServers.isEmpty()) {
                                task.cancel();
                                task = null;
                                return;
                            }
                            final String json = gson.toJson(getFullTL());
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

    public LinkedHashMap<String, Long> getFullTL() {
        final HashMap<String, Long> pTempMap = new HashMap<>(main.playtimeCache);

        main.getIterator().forEachRemaining(player -> {
            if(!pTempMap.containsKey(player)) {
                pTempMap.put(player, main.getSavedPt(player));
            }
        });

        return pTempMap.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed()) // Sort by pt
                .collect(Collectors.toMap(
                        Map.Entry::getKey,
                        Map.Entry::getValue,
                        (e1, e2) -> e1,
                        LinkedHashMap::new));
    }
}
