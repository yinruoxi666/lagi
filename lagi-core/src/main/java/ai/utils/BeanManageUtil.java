package ai.utils;

import ai.annotation.Component;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URL;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;


public class BeanManageUtil {

    private static final Logger log = LoggerFactory.getLogger(BeanManageUtil.class);
    public static Map<Class<?>, Object> beanMaps = new ConcurrentHashMap<>();

    static {
        String[] packages = new String[] {
                "ai.llm.hook.impl",
        };
        for (String packageName : packages) {
            try {
                Set<Object> objects = scanAndCreateObjects(packageName);
                objects.forEach(object -> beanMaps.put(object.getClass(), object));
            } catch (Exception ignored) {
            }
        }
    }


    public static <T> List<T> getBeansByType(Class<T> type) {
        List<T> result = new ArrayList<>();
        for (Map.Entry<Class<?>, Object> entry : beanMaps.entrySet()) {
            Object instance = entry.getValue();
            if (type.isInstance(instance)) {
                result.add(type.cast(instance));
            }
        }
        return sortList(result);
    }

    public static <T> List<T> sortList(List<T> list) {
        return list.stream().sorted(new Comparator<T>() {
            @Override
            public int compare(T o1, T o2) {
                int order1 = getOrderValue(o1.getClass());
                int order2 = getOrderValue(o2.getClass());
                return order1 - order2;
            }

            private int getOrderValue(Class<?> clazz) {
                if (clazz.isAnnotationPresent(ai.annotation.Order.class)) {
                    ai.annotation.Order order = clazz.getAnnotation(ai.annotation.Order.class);
                    return order.value();
                }
                return 2; // 默认值为 2
            }
        }).collect(Collectors.toList());
    }


    /**
     * Scan all classes in the specified package and create corresponding instance objects
     * @param packageName The package name to scan (e.g.: com.example.demo)
     * @return Object collection of all instantiable classes in the package
     * @throws Exception Exceptions during scanning/instantiation process
     */
    public static Set<Object> scanAndCreateObjects(String packageName) throws Exception {
        Set<Class<?>> classSet = scanPackage(packageName);
        Set<Object> objectSet = new LinkedHashSet<>();

        for (Class<?> clazz : classSet) {
            try {
                Component annotation = clazz.getAnnotation(Component.class);
                if(annotation == null) {
                    continue;
                }
                Object instance = clazz.getConstructor().newInstance();
                objectSet.add(instance);
            } catch (Exception e) {
                log.error("Failed to create instance for class: " + clazz.getName(), e);
            }
        }
        return objectSet;
    }

    /**
     * Scan all instantiable classes in the specified package (non-interface, non-abstract class, non-inner class)
     * @param packageName Package name
     * @return Instantiable class collection
     * @throws IOException IO exception
     * @throws ClassNotFoundException Class not found exception
     */
    private static Set<Class<?>> scanPackage(String packageName) throws IOException, ClassNotFoundException {
        Set<Class<?>> classSet = new LinkedHashSet<>();
        String packagePath = packageName.replace('.', '/');
        ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        Enumeration<URL> resources = classLoader.getResources(packagePath);

        while (resources.hasMoreElements()) {
            URL resource = resources.nextElement();
            String protocol = resource.getProtocol();

            if ("file".equals(protocol)) {
                String filePath = URLDecoder.decode(resource.getFile(), String.valueOf(StandardCharsets.UTF_8));
                scanFileClasses(packageName, filePath, classSet);
            } else if ("jar".equals(protocol)) {
                JarURLConnection jarConn = (JarURLConnection) resource.openConnection();
                JarFile jarFile = jarConn.getJarFile();
                Enumeration<JarEntry> entries = jarFile.entries();
                while (entries.hasMoreElements()) {
                    JarEntry entry = entries.nextElement();
                    String entryName = entry.getName();
                    if (entryName.startsWith(packagePath) && entryName.endsWith(".class")) {
                        String className = entryName.replace('/', '.').substring(0, entryName.length() - 6);
                        loadAndCheckClass(className, classSet);
                    }
                }
            }
        }
        return classSet;
    }

    /**
     * Scan .class files in file system, load and validate classes
     * @param packageName Package name
     * @param filePath File path corresponding to package
     * @param classSet Store classes that meet conditions
     * @throws ClassNotFoundException Class not found exception
     */
    private static void scanFileClasses(String packageName, String filePath, Set<Class<?>> classSet) throws ClassNotFoundException {
        File dir = new File(filePath);
        File[] files = dir.listFiles();
        if (files == null) return;

        for (File file : files) {
            String fileName = file.getName();
            if (file.isDirectory()) {
                scanFileClasses(packageName + "." + fileName, file.getAbsolutePath(), classSet);
            } else if (fileName.endsWith(".class") && !fileName.contains("$")) {
                String className = packageName + "." + fileName.substring(0, fileName.length() - 6);
                loadAndCheckClass(className, classSet);
            }
        }
    }

    /**
     * Load class and validate whether it is instantiable (non-interface, non-abstract class)
     * @param className Full class name
     * @param classSet Store classes that meet conditions
     * @throws ClassNotFoundException Class not found exception
     */
    private static void loadAndCheckClass(String className, Set<Class<?>> classSet) throws ClassNotFoundException {
        Class<?> clazz = Class.forName(className);
        if (!clazz.isInterface() && !java.lang.reflect.Modifier.isAbstract(clazz.getModifiers())) {
            classSet.add(clazz);
        }
    }

}
