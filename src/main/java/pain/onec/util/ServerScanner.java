package pain.onec.util;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;

import java.util.stream.Collectors;
import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.locks.ReentrantLock;

import net.minecraft.client.MinecraftClient;
import net.minecraft.util.Util;
import net.minecraft.client.network.ServerInfo;
import net.minecraft.client.network.ServerInfo.ServerType;
import net.minecraft.client.option.ServerList;

/* 
 * Server Scanner
 * By mmorphig
 * Originally translated from a Python script by mmorphig
 * 
 * This is very inefficient, I do not recommend using this
 */

public class ServerScanner {
    private static final int BATCH_SIZE = 2048;
    private static final String OUTPUT_FILE = "OneC/Scanner/minecraft_server_status.ndjson";
    private static final String TEMP_OUTPUT_FILE = "OneC/Scanner/minecraft_server_status_updated.ndjson";
    private String outputFile = OUTPUT_FILE;

    private final Set<String> existingData = ConcurrentHashMap.newKeySet();
    private final BlockingQueue<String> resultQueue = new LinkedBlockingQueue<>();
    private final ReentrantLock writeLock = new ReentrantLock();
    private final ReentrantLock serverListLock = new ReentrantLock();
    private final Gson gson = new Gson();

    public void run(String startIpStr, String endIpStr, Integer port, boolean addToServerList, int threadCount, boolean refreshFromJson, boolean addOnlyActive) {
        try {
            outputFile = refreshFromJson ? TEMP_OUTPUT_FILE : OUTPUT_FILE;

            long startIp = ipToLong(startIpStr);
            long endIp = ipToLong(endIpStr);

            if (!refreshFromJson && startIp > endIp) {
                System.out.println("start_ip must be less than or equal to end_ip");
                return;
            }

            loadExistingData();

            Thread writerThread = new Thread(this::writerThread);
            writerThread.start();

            ExecutorService executor = Executors.newFixedThreadPool(threadCount);

            if (refreshFromJson) {
                // Collect IPs from existingData
                List<Long> ipLongs = new ArrayList<>();
                for (String ip : existingData) {
                    try {
                        long ipLong = ipToLong(ip.trim());
                        ipLongs.add(ipLong);
                    } catch (Exception e) {
                        System.err.println("Invalid IP skipped: " + ip);
                    }
                }

                System.out.println("Converted " + ipLongs.size() + " IP longs from existingData");

                // Split into batches
                List<List<Long>> ipBatches = new ArrayList<>();
                for (int i = 0; i < ipLongs.size(); i += BATCH_SIZE) {
                    ipBatches.add(new ArrayList<>(ipLongs.subList(i, Math.min(i + BATCH_SIZE, ipLongs.size()))));
                }

                System.out.println("Shuffling " + ipBatches.size() + " IP batches.");
                Collections.shuffle(ipBatches);

                for (List<Long> batch : ipBatches) {
                    if (Thread.currentThread().isInterrupted()) {
                        System.out.println("Scan interrupted.");
                        executor.shutdownNow();
                        resultQueue.clear();
                        resultQueue.put("EOF");
                        writerThread.join();
                        return;
                    }

                    List<String> currentBatch = new ArrayList<>();
                    for (long ipLong : batch) {
                        String ip = longToIp(ipLong);
                        if (isPrivateIp(ip)) continue;
                        currentBatch.add(ip);
                    }

                    if (!currentBatch.isEmpty()) {
                        processBatch(currentBatch, executor, port, addToServerList, addOnlyActive);
                    }
                }

            } else {
                // IP range scanning
                List<long[]> blocks = new ArrayList<>();
                for (long blockStart = startIp; blockStart <= endIp; blockStart += BATCH_SIZE) {
                    long blockEnd = Math.min(blockStart + BATCH_SIZE - 1, endIp);
                    blocks.add(new long[]{blockStart, blockEnd});
                }

                System.out.println("Generated " + blocks.size() + " IP range blocks.");
                Collections.shuffle(blocks);

                for (long[] block : blocks) {
                    if (Thread.currentThread().isInterrupted()) {
                        System.out.println("Scan interrupted.");
                        executor.shutdownNow();
                        resultQueue.clear();
                        resultQueue.put("EOF");
                        writerThread.join();
                        return;
                    }

                    List<String> currentBatch = new ArrayList<>();
                    for (long ipLong = block[0]; ipLong <= block[1]; ipLong++) {
                        String ip = longToIp(ipLong);
                        if (isPrivateIp(ip)) continue;
                        currentBatch.add(ip);
                    }

                    if (!currentBatch.isEmpty()) {
                        processBatch(currentBatch, executor, port, addToServerList, addOnlyActive);
                    }
                }
            }

            System.out.println("Done pinging.");

            executor.shutdown();
            executor.awaitTermination(Long.MAX_VALUE, TimeUnit.NANOSECONDS);

            resultQueue.put("EOF");
            writerThread.join();

            if (refreshFromJson) {
                // Overwrite the main file with the updated one
                Files.deleteIfExists(Paths.get(OUTPUT_FILE));
                Files.move(Paths.get(TEMP_OUTPUT_FILE), Paths.get(OUTPUT_FILE));
                System.out.println("Refreshed results saved to " + OUTPUT_FILE);
            } else {
                System.out.println("Scan results saved to " + OUTPUT_FILE);
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void processBatch(List<String> batch, ExecutorService executor, int port, boolean addToServerList, boolean addOnlyActive) throws InterruptedException {
        List<Future<Void>> futures = new ArrayList<>();

        for (String ip : batch) {
            futures.add(executor.submit(() -> {
                pingServer(ip, port, addToServerList, addOnlyActive);
                return null;
            }));
        }

        for (Future<Void> f : futures) {
            try {
                f.get();
            } catch (ExecutionException e) {
                // ignore
            }
        }
    }

    private void pingServer(String ip, int port, boolean addToServerList, boolean addOnlyActive) {
        try (Socket socket = new Socket()) {
            socket.connect(new InetSocketAddress(ip, port), 1000);
            socket.setSoTimeout(1000);

            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream handshake = new DataOutputStream(baos);

            handshake.writeByte(0x00); // Handshake packet id
            writeVarInt(handshake, 765); // version
            writeVarInt(handshake, ip.length());
            handshake.writeBytes(ip);
            handshake.writeShort(port);
            writeVarInt(handshake, 1); 

            writeVarInt(out, baos.size());
            out.write(baos.toByteArray());

            out.writeByte(0x01);
            out.writeByte(0x00);

            readVarInt(in);
            int packetId = readVarInt(in);
            if (packetId != 0x00) return;

            int stringLength = readVarInt(in);
            if (stringLength < 10 || stringLength > 6000) return;

            byte[] data = new byte[stringLength];
            in.readFully(data);
            String json = new String(data, StandardCharsets.UTF_8);

            // Parse the full JSON response
            JsonObject parsed;
            try {
                parsed = gson.fromJson(json, JsonObject.class);
            } catch (Exception e) {
                System.err.println("Invalid JSON from " + ip + ": " + e.getMessage());
                return;
            }

            if (!parsed.has("description")) return;

            String motd = extractMotd(parsed);
            String version = parsed.has("version") && parsed.getAsJsonObject("version").has("name")
                    ? parsed.getAsJsonObject("version").get("name").getAsString()
                    : "unknown";

            int playersOnline = parsed.has("players") && parsed.getAsJsonObject("players").has("online")
                    ? parsed.getAsJsonObject("players").get("online").getAsInt()
                    : 0;

            JsonObject status = new JsonObject();
            status.addProperty("online", true);
            status.addProperty("motd", motd);
            status.addProperty("version", version);
            status.addProperty("players_online", playersOnline);

            if (parsed.has("players")) {
                status.add("players", parsed.get("players"));
            }

            enqueueResult(ip, port, status);

            if (addToServerList && (playersOnline > 0 || !addOnlyActive)) {
                addToMultiplayerList(ip, port);
            }

        } catch (IOException ignored) {
        }
    }

    private String extractMotd(JsonObject parsed) {
        try {
            JsonElement desc = parsed.get("description");
            if (desc.isJsonPrimitive()) {
                return desc.getAsString();
            } else if (desc.isJsonObject()) {
                JsonObject descObj = desc.getAsJsonObject();
                if (descObj.has("text")) {
                    return descObj.get("text").getAsString();
                }
            }
        } catch (Exception e) {
            System.err.println("Error extracting MOTD: " + e.getMessage());
        }
        return "Unknown MOTD";
    }

    private String extractVersion(String json) {
        try {
            int versionIndex = json.indexOf("\"version\"");
            if (versionIndex == -1) return "";
            int nameIndex = json.indexOf("\"name\"", versionIndex);
            if (nameIndex == -1) return "";
            int startQuote = json.indexOf('"', nameIndex + 7);
            int endQuote = json.indexOf('"', startQuote + 1);
            if (startQuote != -1 && endQuote != -1) {
                return json.substring(startQuote + 1, endQuote);
            }
        } catch (Exception e) {
            // ignore parsing errors
        }
        return "";
    }

    private void addToMultiplayerList(String ip, int port) {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null) return;

        Util.getMainWorkerExecutor().execute(() -> {
            serverListLock.lock();
            try {
                ServerList serverList = new ServerList(client);
                serverList.loadFile();

                String address = ip + ":" + port;
                for (int i = 0; i < serverList.size(); i++) {
                    ServerInfo info = serverList.get(i);
                    if (info.address.equals(address)) return; // Already exists
                }

                ServerInfo info = new ServerInfo(ip, address, ServerType.OTHER);
                serverList.add(info, false);

                try {
                    serverList.saveFile();
                } catch (Exception e) {
                    System.err.println("Failed to save servers.dat — " + e.getMessage());
                }

            } finally {
                serverListLock.unlock();
            }
        });
    }

    private void enqueueResult(String ip, Integer port, JsonObject status) {
        JsonObject result = new JsonObject();
        result.addProperty("ip", ip);
        result.addProperty("port", "" + port);
        result.add("status", status);
        resultQueue.offer(gson.toJson(result));
    }

    private void writerThread() {
        try (BufferedWriter writer = Files.newBufferedWriter(Paths.get(outputFile), StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            while (true) {
                String item = resultQueue.take();
                if ("EOF".equals(item)) break;

                writeLock.lock();
                try {
                    writer.write(item);
                    writer.newLine();
                    writer.flush();
                } finally {
                    writeLock.unlock();
                }
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void loadExistingData() {
        existingData.clear();
        File file = new File("OneC/Scanner/minecraft_server_status.ndjson");
        if (!file.exists()) return;

        try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = reader.readLine()) != null) {
                try {
                    JsonObject obj = gson.fromJson(line, JsonObject.class);
                    if (obj.has("ip")) {
                        String ip = obj.get("ip").getAsString().trim();
                        if (!ip.isEmpty()) {
                            existingData.add(ip);
                        } else {
                            System.err.println("Skipped invalid IP: " + ip);
                        }
                    }
                } catch (Exception e) {
                    System.err.println("Malformed JSON line: " + line);
                }
            }
        } catch (IOException e) {
            System.err.println("Failed to read file: " + file.getName());
            e.printStackTrace();
        }
    }


    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0;
        int position = 0;
        byte currentByte;

        do {
            if (position == 35) throw new IOException("VarInt too big");
            currentByte = in.readByte();
            value |= (currentByte & 0x7F) << (position++ * 7);
        } while ((currentByte & 0x80) == 0x80);

        return value;
    }

    private long ipToLong(String ipAddress) {
        String[] ipParts = ipAddress.trim().split("\\.");
        if (ipParts.length != 4) {
            throw new IllegalArgumentException("Invalid IP address: " + ipAddress);
        }

        long result = 0;
        for (int i = 0; i < 4; i++) {
            if (ipParts[i].isEmpty()) {
                throw new IllegalArgumentException("Empty octet in IP address: " + ipAddress);
            }
            result <<= 8;
            result |= Integer.parseInt(ipParts[i]) & 0xFF;
        }
        return result;
    }


    private String longToIp(long ip) {
        return String.format("%d.%d.%d.%d",
            (ip >> 24) & 0xFF,
            (ip >> 16) & 0xFF,
            (ip >> 8) & 0xFF,
            ip & 0xFF);
    }

    private boolean isPrivateIp(String ip) {
        return ip.startsWith("10.") || ip.startsWith("192.168.") || ip.startsWith("172.16.") || ip.startsWith("172.17.") ||
               ip.startsWith("172.18.") || ip.startsWith("172.19.") || ip.startsWith("172.20.") || ip.startsWith("172.21.") ||
               ip.startsWith("172.22.") || ip.startsWith("172.23.") || ip.startsWith("172.24.") || ip.startsWith("172.25.") ||
               ip.startsWith("172.26.") || ip.startsWith("172.27.") || ip.startsWith("172.28.") || ip.startsWith("172.29.") ||
               ip.startsWith("172.30.") || ip.startsWith("172.31.") || ip.equals("127.0.0.1");
    }
}
