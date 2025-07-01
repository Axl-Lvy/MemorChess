package proj.memorchess.axl.core.config;

import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Properties;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.TimeUnit;
import java.util.logging.Logger;

public class PropertiesLoader {

  public static final PropertiesLoader INSTANCE = new PropertiesLoader();

  private static final Logger LOGGER = Logger.getLogger(PropertiesLoader.class.getName());

  private final Properties properties;
  private final String appConfigPath;

  private PropertiesLoader() {
    appConfigPath = Thread.currentThread().getContextClassLoader().getResource("").getPath() + "app.properties";
    properties = new Properties();
    try (FileInputStream fis = new FileInputStream(appConfigPath)) {
      properties.load(fis);
    } catch (IOException e) {
      LOGGER.warning("Failed to load app.properties.");
    }

    Timer t = new Timer();
    t.schedule(new TimerTask() {
      @Override
      public void run() {
        store();
      }
    }, 0, TimeUnit.MINUTES.toMillis(1));
  }

  public void store() {
    try (FileWriter fw = new FileWriter(appConfigPath)) {
      properties.store(fw, "store to properties file");
    } catch (IOException e) {
      LOGGER.warning("Failed to store app.properties.");
    }
  }

  public Properties getProperties() {
    return properties;
  }
}
