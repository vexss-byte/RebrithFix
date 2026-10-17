package com.nebula.deathcinema;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.fabricmc.loader.api.FabricLoader;

import java.nio.file.Files;
import java.nio.file.Path;

public class Config {
    public double waitSeconds = 300; // default 5 menit
    public boolean set = false;
    public String dim = "";
    public double x, y, z;
    public float yaw, pitch;

    public static Config I = new Config();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    static Path path() { return FabricLoader.getInstance().getConfigDir().resolve("nebuladeath.json"); }

    public static void load() {
        try {
            if (Files.exists(path())) {
                Config c = GSON.fromJson(Files.readString(path()), Config.class);
                if (c != null) I = c;
            }
        } catch (Exception e) { e.printStackTrace(); }
    }

    public static void save() {
        try { Files.writeString(path(), GSON.toJson(I)); } catch (Exception e) { e.printStackTrace(); }
    }
}
