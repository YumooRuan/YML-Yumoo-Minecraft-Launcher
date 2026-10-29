package dev.yml.core.game;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.EOFException;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

/**
 * Minimal reader/writer for Minecraft's servers.dat (gzip compressed NBT).
 * Only name/ip/icon fields are preserved; enough for editing outside the game.
 */
public final class ServersDat {

    public record Server(String name, String ip) {
    }

    private ServersDat() {
    }

    public static List<Server> load(Path file) {
        List<Server> servers = new ArrayList<>();
        if (file == null || !Files.isRegularFile(file)) {
            return servers;
        }
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(Files.newInputStream(file))))) {
            int type = in.readUnsignedByte();
            if (type != 10) {
                return servers;
            }
            readString(in);
            while (true) {
                int tag = in.readUnsignedByte();
                if (tag == 0) {
                    break;
                }
                String name = readString(in);
                if (tag == 9 && "servers".equals(name)) {
                    int elementType = in.readUnsignedByte();
                    int length = in.readInt();
                    for (int i = 0; i < length; i++) {
                        if (elementType != 10) {
                            skip(in, elementType);
                            continue;
                        }
                        String serverName = "";
                        String ip = "";
                        while (true) {
                            int child = in.readUnsignedByte();
                            if (child == 0) {
                                break;
                            }
                            String key = readString(in);
                            if (child == 8) {
                                String value = readString(in);
                                if ("name".equals(key)) {
                                    serverName = value;
                                } else if ("ip".equals(key)) {
                                    ip = value;
                                }
                            } else {
                                skip(in, child);
                            }
                        }
                        if (!ip.isBlank()) {
                            servers.add(new Server(serverName.isBlank() ? ip : serverName, ip));
                        }
                    }
                } else {
                    skip(in, tag);
                }
            }
        } catch (Exception ignored) {
        }
        return servers;
    }

    public static void save(Path file, List<Server> servers) throws IOException {
        Files.createDirectories(file.getParent());
        try (DataOutputStream out = new DataOutputStream(new BufferedOutputStream(
                new GZIPOutputStream(Files.newOutputStream(file))))) {
            out.writeByte(10);
            writeString(out, "");
            out.writeByte(9);
            writeString(out, "servers");
            out.writeByte(10);
            out.writeInt(servers.size());
            for (Server server : servers) {
                out.writeByte(8);
                writeString(out, "name");
                writeString(out, server.name() == null ? "" : server.name());
                out.writeByte(8);
                writeString(out, "ip");
                writeString(out, server.ip() == null ? "" : server.ip());
                out.writeByte(0);
            }
            out.writeByte(0);
        }
    }

    private static String readString(DataInputStream in) throws IOException {
        int length = in.readUnsignedShort();
        byte[] bytes = new byte[length];
        in.readFully(bytes);
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static void writeString(DataOutputStream out, String value) throws IOException {
        byte[] bytes = value.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        out.writeShort(bytes.length);
        out.write(bytes);
    }

    private static void skip(DataInputStream in, int type) throws IOException {
        switch (type) {
            case 1 -> in.skipNBytes(1);
            case 2 -> in.skipNBytes(2);
            case 3 -> in.skipNBytes(4);
            case 4 -> in.skipNBytes(8);
            case 5 -> in.skipNBytes(4);
            case 6 -> in.skipNBytes(8);
            case 7 -> in.skipNBytes(in.readInt());
            case 8 -> readString(in);
            case 9 -> {
                int element = in.readUnsignedByte();
                int length = in.readInt();
                for (int i = 0; i < length; i++) {
                    skip(in, element);
                }
            }
            case 10 -> {
                while (true) {
                    int child = in.readUnsignedByte();
                    if (child == 0) {
                        break;
                    }
                    readString(in);
                    skip(in, child);
                }
            }
            case 11 -> in.skipNBytes(in.readInt() * 4L);
            case 12 -> in.skipNBytes(in.readInt() * 8L);
            default -> throw new EOFException("Unknown NBT tag " + type);
        }
    }
}
