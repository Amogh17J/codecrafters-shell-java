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

    public static void main(String[] args) throws Exception {
        Scanner sc = new Scanner(System.in);
        File currentDirectory = new File(System.getProperty("user.dir"));

        while (true) {
            System.out.print("$ ");
            String command = sc.nextLine();

            if (command.equals("exit") || command.equals("exit 0")) {
                break;
            }

            else if (command.equals("pwd")) {
                System.out.println(currentDirectory.getAbsolutePath());
            }

            else if (command.startsWith("cd ")) {
                String path = command.substring(3);

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

            else if (command.startsWith("echo")) {
                List<String> tokens = parseCommand(command);
                StringBuilder output = new StringBuilder();
                String outputFile = null;
                String errorFile = null;
                boolean appendOut = false;
                boolean appendErr = false;
                int redirectIndex = tokens.size();

                for (int i = 1; i < tokens.size(); i++) {
                    String tok = tokens.get(i);
                    if (tok.equals(">") || tok.equals("1>") || tok.equals(">>") || tok.equals("1>>")
                            || tok.equals("2>") || tok.equals("2>>")) {
                        redirectIndex = i;
                        break;
                    }
                }

                // Scan all redirect tokens after redirectIndex (handles stdout and stderr together)
                for (int i = redirectIndex; i < tokens.size(); i++) {
                    String tok = tokens.get(i);
                    if ((tok.equals(">") || tok.equals("1>") || tok.equals(">>") || tok.equals("1>>"))
                            && i + 1 < tokens.size()) {
                        outputFile = tokens.get(i + 1);
                        appendOut = tok.equals(">>") || tok.equals("1>>");
                        i++;
                    } else if ((tok.equals("2>") || tok.equals("2>>")) && i + 1 < tokens.size()) {
                        errorFile = tokens.get(i + 1);
                        appendErr = tok.equals("2>>");
                        i++;
                    }
                }

                for (int i = 1; i < redirectIndex; i++) {
                    if (i > 1) {
                        output.append(" ");
                    }
                    output.append(tokens.get(i));
                }

                // Handle stdout: either print to terminal or write to file
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

                // Handle stderr redirect: echo never writes to stderr,
                // but the target file must still be created (or left untouched if appending).
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
                        // nothing to write; echo produces no stderr output
                    } catch (IOException e) {
                        System.out.println("echo: " + e.getMessage());
                    }
                }
            }

            else if (command.startsWith("type ")) {
                String cmd = command.substring(5);

                if (cmd.equals("echo") || cmd.equals("exit") || cmd.equals("type")
                        || cmd.equals("pwd") || cmd.equals("cd")) {
                    System.out.println(cmd + " is a shell builtin");
                } else {
                    String path = System.getenv("PATH");
                    String[] dirs = path.split(":");
                    boolean found = false;

                    for (String dir : dirs) {
                        File file = new File(dir, cmd);
                        if (file.isFile() && file.canExecute()) {
                            System.out.println(cmd + " is " + file.getAbsolutePath());
                            found = true;
                            break;
                        }
                    }

                    if (!found) {
                        System.out.println(cmd + ": not found");
                    }
                }
            }

            else {
                List<String> parts = parseCommand(command);

                if (parts.isEmpty()) {
                    continue;
                }

                String outputFile = null;
                String errorFile = null;
                String redirectType = null;
                boolean append = false;

                for (int i = 0; i < parts.size(); i++) {
                    String tok = parts.get(i);
                    if (tok.equals(">") || tok.equals("1>") || tok.equals(">>") || tok.equals("1>>")) {
                        if (i + 1 < parts.size()) {
                            outputFile = parts.get(i + 1);
                        }
                        redirectType = "stdout";
                        append = tok.equals(">>") || tok.equals("1>>");
                        parts = new ArrayList<>(parts.subList(0, i));
                        break;
                    }
                    if (tok.equals("2>") || tok.equals("2>>")) {
                        if (i + 1 < parts.size()) {
                            errorFile = parts.get(i + 1);
                        }
                        redirectType = "stderr";
                        append = tok.equals("2>>");
                        parts = new ArrayList<>(parts.subList(0, i));
                        break;
                    }
                }

                if (parts.isEmpty()) {
                    continue;
                }

                String cmd = parts.get(0);
                String path = System.getenv("PATH");
                String[] dirs = path.split(":");
                File executable = null;

                for (String dir : dirs) {
                    File file = new File(dir, cmd);
                    if (file.isFile() && file.canExecute()) {
                        executable = file;
                        break;
                    }
                }

                if (executable == null) {
                    System.out.println(command + ": command not found");
                    continue;
                }

                List<String> commandList = new ArrayList<>(parts);

                ProcessBuilder pb = new ProcessBuilder(commandList);
                pb.directory(currentDirectory);

                Process process = pb.start();

                BufferedReader stdoutReader = new BufferedReader(
                        new InputStreamReader(process.getInputStream()));
                BufferedReader stderrReader = new BufferedReader(
                        new InputStreamReader(process.getErrorStream()));

                // --- stdout handling ---
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
                        boolean first = true;
                        while ((line = stdoutReader.readLine()) != null) {
                            if (!first) {
                                writer.newLine();
                            }
                            writer.write(line);
                            first = false;
                        }
                    }
                } else {
                    String line;
                    while ((line = stdoutReader.readLine()) != null) {
                        System.out.println(line);
                    }
                }
                stdoutReader.close();

                // --- stderr handling ---
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
                        boolean first = true;
                        while ((errLine = stderrReader.readLine()) != null) {
                            if (!first) {
                                errWriter.newLine();
                            }
                            errWriter.write(errLine);
                            first = false;
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
    }
}