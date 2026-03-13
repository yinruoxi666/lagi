package ai.utils;


public class CommandLineOptions {


    private Integer port;

    private String sqliteDbPath;

    private String configPath;

    public Integer getPort() {
        return port;
    }

    public String getSqliteDbPath() {
        return sqliteDbPath;
    }

    public String getConfigPath() {
        return configPath;
    }

    public int getPortOrDefault(int defaultPort) {
        return port != null ? port : defaultPort;
    }

    public static CommandLineOptions parse(String[] args) {
        CommandLineOptions options = new CommandLineOptions();

        if (args == null) {
            return options;
        }

        for (int i = 0; i < args.length; i++) {
            String arg = args[i];
            if (arg == null) {
                continue;
            }

            if (arg.startsWith("--port")) {
                String value = extractValue(arg, args, i);
                if (value != null) {
                    try {
                        options.port = Integer.parseInt(value);
                    } catch (NumberFormatException e) {
                        System.err.println("Invalid port value: " + value);
                    }
                }
            } else if (arg.startsWith("--db")) {
                String value = extractValue(arg, args, i);
                if (value != null) {
                    options.sqliteDbPath = value;
                }
            } else if (arg.startsWith("--config")) {
                String value = extractValue(arg, args, i);
                if (value != null) {
                    options.configPath = value;
                }
            }
        }

        return options;
    }

    private static String extractValue(String currentArg, String[] args, int index) {
        // --key=value
        int equalIndex = currentArg.indexOf('=');
        if (equalIndex > 0 && equalIndex < currentArg.length() - 1) {
            return currentArg.substring(equalIndex + 1);
        }

        // --key value
        if (index + 1 < args.length) {
            String next = args[index + 1];
            if (next != null && !next.startsWith("--")) {
                return next;
            }
        }

        return null;
    }
}

