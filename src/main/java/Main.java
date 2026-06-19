import java.io.*;
import java.util.*;

public class Main {

    private static final Set<String> BUILTINS =
            new HashSet<>(Arrays.asList("exit", "echo", "type", "pwd", "cd"));

    private static final BufferedReader reader =
            new BufferedReader(new InputStreamReader(System.in));

    public static void main(String[] args) throws Exception {

        setTerminalRawMode();
        Runtime.getRuntime().addShutdownHook(new Thread(Main::restoreTerminal));

        String currentDir = System.getProperty("user.dir");

        while (true) {

            String input = readLineWithAutocomplete();
            if (input == null) break;
            if (input.isEmpty()) continue;

            List<String> tokens = parseInput(input);
            if (tokens.isEmpty()) continue;

            // ---------------- REDIRECTION ----------------

            String stdoutFile = null;
            String stderrFile = null;
            boolean appendStdout = false;
            boolean appendStderr = false;

            List<String> commandArgs = new ArrayList<>();

            for (int i = 0; i < tokens.size(); i++) {
                String t = tokens.get(i);

                switch (t) {
                    case ">", "1>":
                        stdoutFile = tokens.get(++i);
                        appendStdout = false;
                        break;
                    case ">>", "1>>":
                        stdoutFile = tokens.get(++i);
                        appendStdout = true;
                        break;
                    case "2>":
                        stderrFile = tokens.get(++i);
                        appendStderr = false;
                        break;
                    case "2>>":
                        stderrFile = tokens.get(++i);
                        appendStderr = true;
                        break;
                    default:
                        commandArgs.add(t);
                }
            }

            if (commandArgs.isEmpty()) continue;

            String command = commandArgs.get(0);
            List<String> argsList = commandArgs.subList(1, commandArgs.size());
            String joinedArgs = String.join(" ", argsList);

            // ---------------- BUILTINS ----------------

            if (command.equals("exit")) {
                break;
            } else if (command.equals("echo")) {
                handleEcho(joinedArgs, stdoutFile, appendStdout, stderrFile);
            } else if (command.equals("pwd")) {
                System.out.println(currentDir);
            } else if (command.equals("type")) {
                System.out.println(checkType(joinedArgs));
            } else if (command.equals("cd")) {
                currentDir = handleCd(argsList, currentDir);
            }

            // ---------------- EXTERNAL COMMAND ----------------

            else {
                try {

                    ProcessBuilder pb = new ProcessBuilder(commandArgs);
                    pb.directory(new File(currentDir));
                    pb.redirectInput(ProcessBuilder.Redirect.INHERIT);

                    // stdout
                    if (stdoutFile != null) {
                        File out = new File(stdoutFile);
                        if (appendStdout) pb.redirectOutput(ProcessBuilder.Redirect.appendTo(out));
                        else pb.redirectOutput(out);
                    } else {
                        pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                    }

                    // stderr
                    if (stderrFile != null) {
                        File err = new File(stderrFile);
                        if (appendStderr) pb.redirectError(ProcessBuilder.Redirect.appendTo(err));
                        else pb.redirectError(err);
                    } else {
                        pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                    }

                    pb.start().waitFor();

                } catch (IOException e) {
                    System.out.println(command + ": command not found");
                }
            }
        }

        restoreTerminal();
    }

    // ---------------- RAW INPUT WITH AUTOCOMPLETE ----------------

    private static String readLineWithAutocomplete() throws IOException {

        StringBuilder buffer = new StringBuilder();
        print("$ ");

        while (true) {

            int r = reader.read();
            if (r == -1) return null;

            char c = (char) r;

            if (c == '\r' || c == '\n') {
                System.out.print("\r\n");
                return buffer.toString().trim();
            } else if (c == '\t') {
                handleAutocomplete(buffer);
            } else if (c == 127) { // backspace
                if (buffer.length() > 0) {
                    buffer.deleteCharAt(buffer.length() - 1);
                    redraw(buffer);
                }
            } else {
                buffer.append(c);
                System.out.print(c);
                System.out.flush();
            }
        }
    }

    private static void handleAutocomplete(StringBuilder buffer) {

        String current = buffer.toString();

        List<String> matches = new ArrayList<>();
        for (String cmd : BUILTINS) {
            if (cmd.startsWith(current)) {
                matches.add(cmd);
            }
        }

        if (matches.size() == 1) {
            buffer.setLength(0);
            buffer.append(matches.get(0)).append(" ");
            redraw(buffer);
        }
    }

    private static void redraw(StringBuilder buffer) {
        print("$ " + buffer.toString() + " ");
        print("$ " + buffer.toString());
    }

    private static void print(String msg) {
        System.out.print("\r" + msg);
        System.out.flush();
    }

    // ---------------- TERMINAL CONTROL ----------------

    private static void setTerminalRawMode() {
        try {
            Process p =
                    new ProcessBuilder("/bin/sh", "-c", "stty -icanon -echo min 1 time 0")
                            .redirectInput(ProcessBuilder.Redirect.INHERIT)
                            .start();
            p.waitFor();
        } catch (Exception ignored) {
        }
    }

    private static void restoreTerminal() {
        try {
            Process p =
                    new ProcessBuilder("/bin/sh", "-c", "stty sane")
                            .redirectInput(ProcessBuilder.Redirect.INHERIT)
                            .start();
            p.waitFor();
        } catch (Exception ignored) {
        }
    }

    // ---------------- BUILTIN HELPERS ----------------

    private static void handleEcho(
            String text, String stdoutFile, boolean append, String stderrFile) throws IOException {

        if (stdoutFile != null) {
            try (FileWriter fw = new FileWriter(stdoutFile, append)) {
                fw.write(text + System.lineSeparator());
            }
        } else {
            System.out.println(text);
        }

        if (stderrFile != null) {
            new FileWriter(stderrFile, false).close();
        }
    }

    private static String handleCd(List<String> args, String currentDir) throws IOException {

        String target = args.isEmpty() ? System.getenv("HOME") : args.get(0);
        String home = System.getenv("HOME");

        if (target.equals("~")) {
            target = home;
        } else if (target.startsWith("~" + File.separator)) {
            target = home + target.substring(1);
        }

        File dir = new File(target);
        if (!dir.isAbsolute()) {
            dir = new File(currentDir, target);
        }

        if (dir.exists() && dir.isDirectory()) {
            return dir.getCanonicalPath();
        } else {
            System.out.println("cd: " + target + ": No such file or directory");
            return currentDir;
        }
    }

    private static String checkType(String command) {

        if (BUILTINS.contains(command)) {
            return command + " is a shell builtin";
        }

        String path = System.getenv("PATH");
        if (path == null) return command + ": not found";

        for (String dir : path.split(File.pathSeparator)) {
            File file = new File(dir, command);
            if (file.exists() && file.canExecute()) {
                return command + " is " + file.getAbsolutePath();
            }
        }

        return command + ": not found";
    }

    // ---------------- PARSER WITH QUOTES ----------------

    private static List<String> parseInput(String input) {

        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        boolean singleQuote = false;
        boolean doubleQuote = false;
        boolean escape = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            if (escape) {
                current.append(ch);
                escape = false;
                continue;
            }

            if (ch == '\\' && !singleQuote) {
                escape = true;
                continue;
            }

            if (ch == '\'' && !doubleQuote) {
                singleQuote = !singleQuote;
                continue;
            }

            if (ch == '"' && !singleQuote) {
                doubleQuote = !doubleQuote;
                continue;
            }

            if (Character.isWhitespace(ch) && !singleQuote && !doubleQuote) {
                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }
            } else {
                current.append(ch);
            }
        }

        if (current.length() > 0) {
            tokens.add(current.toString());
        }

        return tokens;
    }
}
