package me.chrr.camerapture.config;

import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import me.chrr.camerapture.Camerapture;

public class ConfigManager {
   private static final Gson GSON = new GsonBuilder()
      .setFieldNamingPolicy(FieldNamingPolicy.LOWER_CASE_WITH_UNDERSCORES)
      .addSerializationExclusionStrategy(new ConfigManager.SkipDeprecatedStrategy())
      .setPrettyPrinting()
      .create();
   private final Config config = new Config();

   public Config getConfig() {
      return this.config;
   }

   public void load() {
      Path clientPath = this.getPath("client");
      Path serverPath = this.getPath("server");

      try {
         if (clientPath.toFile().isFile()) {
            this.config.client = (Config.Client)GSON.fromJson(Files.readString(clientPath), Config.Client.class);
            this.config.client.upgrade();
         }

         if (serverPath.toFile().isFile()) {
            this.config.server = (Config.Server)GSON.fromJson(Files.readString(serverPath), Config.Server.class);
            this.config.server.upgrade();
         }

         this.save();
      } catch (IOException e) {
         Camerapture.LOGGER.error("failed to load config", e);
      }
   }

   public void save() {
      try {
         if (Camerapture.PLATFORM.isClientSide()) {
            Files.writeString(this.getPath("client"), GSON.toJson(this.config.client));
         }

         Files.writeString(this.getPath("server"), GSON.toJson(this.config.server));
      } catch (IOException e) {
         Camerapture.LOGGER.error("failed to save config", e);
      }
   }

   private Path getPath(String environment) {
      return Camerapture.PLATFORM.getConfigFolder().resolve("camerapture." + environment + ".json");
   }

   private static class SkipDeprecatedStrategy implements ExclusionStrategy {
      public boolean shouldSkipField(FieldAttributes f) {
         return f.getAnnotation(DeprecatedConfigOption.class) != null;
      }

      public boolean shouldSkipClass(Class<?> clazz) {
         return false;
      }
   }
}
