package ai.utils;

import java.io.File;
import java.io.FileFilter;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;

public class LoadJarUtil {

    public static URLClassLoader loadAllJarsFromFolder(String jarFolderPath)  {
        File jarFolder = new File(jarFolderPath);
        if (!jarFolder.exists() || !jarFolder.isDirectory()) {
            return null;
        }

        FileFilter jarFilter = file -> file.isFile() && (file.getName().endsWith(".jar") || file.getName().endsWith(".zip"));

        File[] jarFiles = jarFolder.listFiles(jarFilter);
        if (jarFiles == null || jarFiles.length == 0) {
            return new URLClassLoader(new URL[0]);
        }
        try {
            URL[] urls = new URL[jarFiles.length];
            for (int i = 0; i < jarFiles.length; i++) {
                urls[i] = jarFiles[i].toURI().toURL();
            }
            return new URLClassLoader(urls, LoadJarUtil.class.getClassLoader());
        } catch (MalformedURLException ignored) {

        }
        return null;
    }
}
