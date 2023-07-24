package com.microsoft.java.bs.core.internal.gradle;

import java.io.File;
import java.net.URI;
import java.nio.file.Paths;
import java.util.List;

import org.gradle.internal.impldep.org.apache.commons.lang.StringUtils;
import org.gradle.tooling.BuildLauncher;
import org.gradle.tooling.GradleConnector;
import org.gradle.tooling.ModelBuilder;
import org.gradle.tooling.ProjectConnection;

import com.microsoft.java.bs.core.internal.model.Preferences;

/**
 * Gradle Tooling API utils.
 */
public class Utils {
  private Utils() {}

  /**
   * The file name of the init script.
   */
  private static final String INIT_GRADLE_SCRIPT = "init.gradle";

  /**
   * The environment variable for Gradle home.
   */
  private static final String GRADLE_HOME = "GRADLE_HOME";

  /**
   * The environment variable for Gradle user home.
   */
  private static final String GRADLE_USER_HOME = "GRADLE_USER_HOME";

  /**
   * Get the Daemon connection for the project.
   *
   * @param projectUri The project uri.
   */ 
  public static ProjectConnection getProjectConnection(URI projectUri,
      Preferences preferences) {
    return getProjectConnection(new File(projectUri), preferences);
  }

  /**
   * Get the Daemon connection for the project.
   *
   * @param project The project.
   */
  public static ProjectConnection getProjectConnection(File project, Preferences preferences) {
    GradleConnector connector = GradleConnector.newConnector()
        .forProjectDirectory(project);

    File gradleUserHome = getGradleUserHomeFile(preferences.getGradleUserHome());
    if (gradleUserHome != null && gradleUserHome.exists()) {
      connector.useGradleUserHomeDir(gradleUserHome);
    }

    if (preferences.getGradleVersion() != null) {
      connector.useGradleVersion(preferences.getGradleVersion());
    } else if (preferences.getGradleHome() != null) {
      File gradleHome = getGradleHome(preferences.getGradleHome());
      if (gradleHome != null && gradleHome.exists()) {
        connector.useInstallation(gradleHome);
      }
    } else {
      connector.useBuildDistribution();
    }
    return connector.connect();
  }

  /**
   * Get the model builder for the given project connection.
   *
   * @param <T> The type of the model.
   * @param connection The project connection.
   * @param clazz The class of the model.
   */
  public static <T> ModelBuilder<T> getModelBuilder(ProjectConnection connection,
      Preferences preferences, Class<T> clazz) {
    ModelBuilder<T> modelBuilder = connection.model(clazz);

    File gradleJavaHomeFile = getGradleJavaHomeFile(preferences.getGradleJavaHome());
    if (gradleJavaHomeFile != null && gradleJavaHomeFile.exists()) {
      modelBuilder.setJavaHome(gradleJavaHomeFile);
    }

    List<String> gradleJvmArguments = preferences.getGradleJvmArguments();
    if (gradleJvmArguments != null && !gradleJvmArguments.isEmpty()) {
      modelBuilder.setJvmArguments(gradleJvmArguments);
    }

    List<String> gradleArguments = preferences.getGradleArguments();
    if (gradleArguments != null && !gradleArguments.isEmpty()) {
      modelBuilder.withArguments(gradleArguments);
    }
    return modelBuilder;
  }

  /**
   * Get the Build Launcher.
   *
   * @param connection The project connection.
   * @param preferences The preferences.
   */
  public static BuildLauncher getBuildLauncher(ProjectConnection connection,
      Preferences preferences) {
    BuildLauncher launcher = connection.newBuild();

    File gradleJavaHomeFile = getGradleJavaHomeFile(preferences.getGradleJavaHome());
    if (gradleJavaHomeFile != null && gradleJavaHomeFile.exists()) {
      launcher.setJavaHome(gradleJavaHomeFile);
    }

    List<String> gradleJvmArguments = preferences.getGradleJvmArguments();
    if (gradleJvmArguments != null && !gradleJvmArguments.isEmpty()) {
      launcher.setJvmArguments(gradleJvmArguments);
    }

    List<String> gradleArguments = preferences.getGradleArguments();
    if (gradleArguments != null && !gradleArguments.isEmpty()) {
      launcher.withArguments(gradleArguments);
    }
    return launcher;
  }

  public static File getInitScriptFile() {
    return Paths.get(System.getProperty("plugin.dir"), INIT_GRADLE_SCRIPT).toFile();
  }

  static File getGradleUserHomeFile(String gradleUserHome) {
    if (StringUtils.isNotBlank(gradleUserHome)) {
      return new File(gradleUserHome);
    }
    
    return getFileFromEnvOrProperty(GRADLE_USER_HOME);
  }

  static File getGradleHome(String gradleHome) {
    if (StringUtils.isNotBlank(gradleHome)) {
      return new File(gradleHome);
    }

    return getFileFromEnvOrProperty(GRADLE_HOME);
  }

  /**
   * Get the path specified by the key from environment variables or system properties.
   * If the path is not empty, an <code>File</code> instance will be returned.
   * Otherwise, <code>null</code> will be returned.
   */
  static File getFileFromEnvOrProperty(String key) {
    String value = System.getenv().get(key);
    if (StringUtils.isBlank(value)) {
      value = System.getProperties().getProperty(key);
    }
    if (StringUtils.isNotBlank(value)) {
      return new File(value);
    }

    return null;
  }

  static File getGradleJavaHomeFile(String gradleJavaHome) {
    if (StringUtils.isNotBlank(gradleJavaHome)) {
      File file = new File(gradleJavaHome);
      if (file.isDirectory()) {
        return file;
      }
    }
    return null;
  }
}
