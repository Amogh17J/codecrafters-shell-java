import java.util.*;
import java.io.*;

public class Main {

    private static List<String> parseCommand(String input) {
        List<String> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        boolean inSingleQuote = false;
        boolean inDoubleQuote = false;

        for (int i = 0; i < input.length(); i++) {
            char ch = input.charAt(i);

            if (!inSingleQuote && !inDoubleQuote && ch == '\\') {
                if (i + 1 < input.length()) {
                    current.append(input.charAt(i + 1));
                    i++;
                }
                continue;
            }

            if (inDoubleQuote && ch == '\\') {
                if (i + 1 < input.length()) {
                    char next = input.charAt(i + 1);
                    if (next == '"' || next == '\\') {
                        current.append(next);
                        i++;
                        continue;
                    }
                }
                current.append(ch);
                continue;
            }

            if (ch == '\'' && !inDoubleQuote) {
                inSingleQuote = !inSingleQuote;
                continue;
            }

            if (ch == '"' && !inSingleQuote) {
                inDoubleQuote = !inDoubleQuote;
                continue;
            }

            if (!inSingleQuote && !inDoubleQuote && ch == '>') {
                boolean isAppend = (i + 1 < input.length() && input.charAt(i + 1) == '>');

                if (current.toString().equals("1")) {
                    current.setLength(0);
                    tokens.add(isAppend ? "1>>" : "1>");
                    if (isAppend) i++;
                    continue;
                }

                if (current.toString().equals("2")) {
                    current.setLength(0);
                    tokens.add(isAppend ? "2>>" : "2>");
                    if (isAppend) i++;
                    continue;
                }

                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }

                tokens.add(isAppend ? ">>" : ">");
                if (isAppend) i++;
                continue;
            }

            if (Character.isWhitespace(ch) && !inSingleQuote && !inDoubleQuote) {
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

    private static final String[] BUILTIN_COMMANDS = { "echo", "exit", "type", "pwd", "cd", "jobs" };

    private static void enableRawMode() throws IOException, InterruptedException {
        new ProcessBuilder("/bin/sh", "-c", "stty -icanon -echo min 1 time 0 </dev/tty")
                .inheritIO().start().waitFor();
    }

    private static void disableRawMode() throws IOException, InterruptedException {
        new ProcessBuilder("/bin/sh", "-c", "stty sane </dev/tty")
                .inheritIO().start().waitFor();
    }

    private static String findCompletion(String partial) {
        if (partial.isEmpty()) {
            return null;
        }
        for (String cmd : BUILTIN_COMMANDS) {
            if (cmd.startsWith(partial)) {
                return cmd;
            }
        }
        return null;
    }

    private static String readLineWithTabCompletion(InputStream in) throws IOException {
        StringBuilder line = new StringBuilder();

        while (true) {
            int b = in.read();

            if (b == -1) {
                if (line.length() == 0) {
                    return null;
                }
                return line.toString();
            }

            char ch = (char) b;

            if (ch == '\t') {
                String completion = findCompletion(line.toString());
                if (completion != null) {
                    String suffix = completion.substring(line.length());
                    line.append(suffix).append(' ');
                    System.out.print(suffix + " ");
                    System.out.flush();
                } else {
                    System.out.print('\u0007');
                    System.out.flush();
                }
                continue;
            }

            if (ch == '\r' || ch == '\n') {
                System.out.print("\r\n");
                System.out.flush();
                return line.toString();
            }

            if (ch == 127 || ch == '\b') {
                if (line.length() > 0) {
                    line.deleteCharAt(line.length() - 1);
                    System.out.print("\b \b");
                    System.out.flush();
                }
                continue;
            }

            if (ch == 3) {
                System.out.print("^C\r\n");
                System.out.flush();
                line.setLength(0);
                continue;
            }

            line.append(ch);
            System.out.print(ch);
            System.out.flush();
        }
    }

    public static void main(String[] args) throws Exception {
        File currentDirectory = new File(System.getProperty("user.dir"));

        boolean rawModeEnabled = false;
        try {
            enableRawMode();
            rawModeEnabled = true;
        } catch (Exception e) {
        }

        Scanner fallbackScanner = rawModeEnabled ? null : new Scanner(System.in);

        try {
            while (true) {
                System.out.print("$ ");
                System.out.flush();

                String commandLine;
                if (rawModeEnabled) {
                    commandLine = readLineWithTabCompletion(System.in);
                    if (commandLine == null) {
                        break;
                    }
                } else {
                    if (!fallbackScanner.hasNextLine()) {
                        break;
                    }
                    commandLine = fallbackScanner.nextLine();
                }

                if (commandLine.trim().isEmpty()) {
                    continue;
                }

                List<String> parts = parseCommand(commandLine);
                if (parts.isEmpty()) {
                    continue;
                }

                boolean isBackground = false;
                if (parts.get(parts.size() - 1).equals("&")) {
                    isBackground = true;
                    parts.remove(parts.size() - 1);
                }

                if (parts.isEmpty()) {
                    continue;
                }

                String cmd = parts.get(0);

                if (cmd.equals("exit")) {
                    break;
                }

                else if (cmd.equals("pwd")) {
                    System.out.println(currentDirectory.getAbsolutePath());
                }

                else if (cmd.equals("jobs")) {
                    continue;
                }

                else if (cmd.equals("cd")) {
                    String path = parts.size() > 1 ? parts.get(1) : "~";

                    if (path.equals("~")) {
                        path = System.getenv("HOME");
                    }

                    File target;
                    if (path.startsWith("/")) {
                        target = new File(path);
                    } else {
                        target = new File(currentDirectory, path);
                    }

                    target = target.getCanonicalFile();

                    if (target.exists() && target.isDirectory()) {
                        currentDirectory = target;
                    } else {
                        System.out.println("cd: " + path + ": No such file or directory");
                    }
                }

                else if (cmd.equals("echo")) {
                    StringBuilder output = new StringBuilder();
                    String outputFile = null;
                    String errorFile = null;
                    boolean appendOut = false;
                    boolean appendErr = false;
                    int redirectIndex = parts.size();

                    for (int i = 1; i < parts.size(); i++) {
                        String tok = parts.get(i);
                        if (tok.equals(">") || tok.equals("1>") || tok.equals(">>") || tok.equals("1>>")
                                || tok.equals("2>") || tok.equals("2>>")) {
                            redirectIndex = i;
                            break;
                        }
                    }

                    for (int i = redirectIndex; i < parts.size(); i++) {
                        String tok = parts.get(i);
                        if ((tok.equals(">") || tok.equals("1>") || tok.equals(">>") || tok.equals("1>>"))
                                && i + 1 < parts.size()) {
                            outputFile = parts.get(i + 1);
                            appendOut = tok.equals(">>") || tok.equals("1>>");
                            i++;
                        } else if ((tok.equals("2>") || tok.equals("2>>")) && i + 1 < parts.size()) {
                            errorFile = parts.get(i + 1);
                            appendErr = tok.equals("2>>");
                            i++;
                        }
                    }

                    for (int i = 1; i < redirectIndex; i++) {
                        if (i > 1) {
                            output.append(" ");
                        }
                        output.append(parts.get(i));
                    }

                    if (outputFile == null) {
                        System.out.println(output);
                    } else {
                        File outFile;
                        if (new File(outputFile).isAbsolute()) {
                            outFile = new File(outputFile);
                        } else {
                            outFile = new File(currentDirectory, outputFile);
                        }

                        File parent = outFile.getParentFile();
                        if (parent != null) {
                            parent.mkdirs();
                        }

                        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile, appendOut))) {
                            writer.write(output.toString());
                            writer.newLine();
                        } catch (IOException e) {
                            System.out.println("echo: " + e.getMessage());
                        }
                    }

                    if (errorFile != null) {
                        File errFile;
                        if (new File(errorFile).isAbsolute()) {
                            errFile = new File(errorFile);
                        } else {
                            errFile = new File(currentDirectory, errorFile);
                        }

                        File parent = errFile.getParentFile();
                        if (parent != null) {
                            parent.mkdirs();
                        }

                        try (BufferedWriter errWriter = new BufferedWriter(new FileWriter(errFile, appendErr))) {
                        } catch (IOException e) {
                            System.out.println("echo: " + e.getMessage());
                        }
                    }
                }

                else if (cmd.equals("type")) {
                    if (parts.size() < 2) {
                        continue;
                    }
                    String targetCmd = parts.get(1);

                    if (targetCmd.equals("echo") || targetCmd.equals("exit") || targetCmd.equals("type")
                            || targetCmd.equals("pwd") || targetCmd.equals("cd") || targetCmd.equals("jobs")) {
                        System.out.println(targetCmd + " is a shell builtin");
                    } else {
                        String pathEnv = System.getenv("PATH");
                        String[] dirs = pathEnv.split(":");
                        boolean found = false;

                        for (String dir : dirs) {
                            File file = new File(dir, targetCmd);
                            if (file.isFile() && file.canExecute()) {
                                System.out.println(targetCmd + " is " + file.getAbsolutePath());
                                found = true;
                                break;
                            }
                        }

                        if (!found) {
                            System.out.println(targetCmd + ": not found");
                        }
                    }
                }

                else {
                    String outputFile = null;
                    String errorFile = null;
                    String redirectType = null;
                    boolean append = false;

                    List<String> execParts = new ArrayList<>(parts);

                    for (int i = 0; i < execParts.size(); i++) {
                        String tok = execParts.get(i);
                        if (tok.equals(">") || tok.equals("1>") || tok.equals(">>") || tok.equals("1>>")) {
                            if (i + 1 < execParts.size()) {
                                outputFile = execParts.get(i + 1);
                            }
                            redirectType = "stdout";
                            append = tok.equals(">>") || tok.equals("1>>");
                            execParts = new ArrayList<>(execParts.subList(0, i));
                            break;
                        }
                        if (tok.equals("2>") || tok.equals("2>>")) {
                            if (i + 1 < execParts.size()) {
                                errorFile = execParts.get(i + 1);
                            }
                            redirectType = "stderr";
                            append = tok.equals("2>>");
                            execParts = new ArrayList<>(execParts.subList(0, i));
                            break;
                        }
                    }

                    if (execParts.isEmpty()) {
                        continue;
                    }

                    String executableCmd = execParts.get(0);
                    String pathEnv = System.getenv("PATH");
                    String[] dirs = pathEnv.split(":");
                    File executable = null;

                    for (String dir : dirs) {
                        File file = new File(dir, executableCmd);
                        if (file.isFile() && file.canExecute()) {
                            executable = file;
                            break;
                        }
                    }

                    if (executable == null) {
                        System.out.println(executableCmd + ": command not found");
                        continue;
                    }

                    ProcessBuilder pb = new ProcessBuilder(execParts);
                    pb.directory(currentDirectory);

                    if (isBackground) {
                        if (redirectType == null) {
                            pb.redirectOutput(ProcessBuilder.Redirect.DISCARD);
                            pb.redirectError(ProcessBuilder.Redirect.DISCARD);
                        }
                        Process process = pb.start();
                        System.out.println("[1] " + process.pid());
                        System.out.flush();
                        continue;
                    }

                    Process process = pb.start();

                    BufferedReader stdoutReader = new BufferedReader(
                            new InputStreamReader(process.getInputStream()));
                    BufferedReader stderrReader = new BufferedReader(
                            new InputStreamReader(process.getErrorStream()));

                    if ("stdout".equals(redirectType)) {
                        File outFile;
                        if (new File(outputFile).isAbsolute()) {
                            outFile = new File(outputFile);
                        } else {
                            outFile = new File(currentDirectory, outputFile);
                        }

                        File parent = outFile.getParentFile();
                        if (parent != null) {
                            parent.mkdirs();
                        }

                        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile, append))) {
                            String line;
                            while ((line = stdoutReader.readLine()) != null) {
                                writer.write(line);
                                writer.newLine();
                            }
                        }
                    } else {
                        String line;
                        while ((line = stdoutReader.readLine()) != null) {
                            System.out.println(line);
                        }
                    }
                    stdoutReader.close();

                    if ("stderr".equals(redirectType)) {
                        File errFile;
                        if (new File(errorFile).isAbsolute()) {
                            errFile = new File(errorFile);
                        } else {
                            errFile = new File(currentDirectory, errorFile);
                        }

                        File parent = errFile.getParentFile();
                        if (parent != null) {
                            parent.mkdirs();
                        }

                        try (BufferedWriter errWriter = new BufferedWriter(new FileWriter(errFile, append))) {
                            String errLine;
                            while ((errLine = stderrReader.readLine()) != null) {
                                errWriter.write(errLine);
                                errWriter.newLine();
                            }
                        }
                    } else {
                        String errLine;
                        while ((errLine = stderrReader.readLine()) != null) {
                            System.err.println(errLine);
                        }
                    }
                    stderrReader.close();

                    process.waitFor();
                }
            }
        } finally {
            if (rawModeEnabled) {
                try {
                    disableRawMode();
                } catch (Exception e) {
                }
            }
        }
    }
}