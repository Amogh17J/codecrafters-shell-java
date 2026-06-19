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

            if (!inSingleQuote
                    && !inDoubleQuote
                    && ch == '>') {

                if (current.length() > 0) {
                    tokens.add(current.toString());
                    current.setLength(0);
                }

                tokens.add(">");

                continue;
            }

            if (Character.isWhitespace(ch)
                    && !inSingleQuote
                    && !inDoubleQuote) {

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

        File currentDirectory =
                new File(System.getProperty("user.dir"));

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
                    System.out.println(
                            "cd: " + path + ": No such file or directory");
                }
            }

            else if (command.startsWith("echo")) {

                List<String> tokens = parseCommand(command);

                StringBuilder output = new StringBuilder();
                String outputFile = null;

                // Check for redirection and extract output file
                for (int i = 1; i < tokens.size(); i++) {
                    if (tokens.get(i).equals(">") || tokens.get(i).equals("1>")) {
                        if (i + 1 < tokens.size()) {
                            outputFile = tokens.get(i + 1);
                        }
                        break;
                    }
                }

                // Collect output tokens (before any redirection)
                for (int i = 1; i < tokens.size(); i++) {
                    if (tokens.get(i).equals(">") || tokens.get(i).equals("1>")) {
                        break;
                    }

                    if (i > 1) {
                        output.append(" ");
                    }
                    output.append(tokens.get(i));
                }

                // Write to file or stdout
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

                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile, false))) {
                        writer.write(output.toString());
                    } catch (IOException e) {
                        System.out.println("echo: " + e.getMessage());
                    }
                }
            }

            else if (command.startsWith("type ")) {

                String cmd = command.substring(5);

                if (cmd.equals("echo")
                        || cmd.equals("exit")
                        || cmd.equals("type")
                        || cmd.equals("pwd")
                        || cmd.equals("cd")) {

                    System.out.println(
                            cmd + " is a shell builtin");
                }

                else {

                    String path = System.getenv("PATH");
                    String[] dirs = path.split(":");

                    boolean found = false;

                    for (String dir : dirs) {

                        File file = new File(dir, cmd);

                        if (file.isFile() && file.canExecute()) {

                            System.out.println(
                                    cmd + " is "
                                            + file.getAbsolutePath());

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

                for (int i = 0; i < parts.size(); i++) {

                    if (parts.get(i).equals(">") ||
                        parts.get(i).equals("1>")) {

                        if (i + 1 < parts.size()) {
                            outputFile = parts.get(i + 1);
                        }

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
                    System.out.println(
                            command + ": command not found");
                    continue;
                }

                List<String> commandList =
                        new ArrayList<>();

                commandList.addAll(parts);

                ProcessBuilder pb =
                        new ProcessBuilder(commandList);

                pb.directory(currentDirectory);

                Process process = pb.start();

                if (outputFile == null) {

                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            process.getInputStream()));

                    String line;

                    while ((line = reader.readLine()) != null) {
                        System.out.println(line);
                    }

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

                    BufferedWriter writer =
                            new BufferedWriter(
                                    new FileWriter(outFile, false));

                    BufferedReader reader =
                            new BufferedReader(
                                    new InputStreamReader(
                                            process.getInputStream()));

                    String line;

                    boolean first = true;

                    while ((line = reader.readLine()) != null) {

                        if (!first) {
                            writer.newLine();
                        }

                        writer.write(line);
                        first = false;
                    }

                    writer.close();
                }

                process.waitFor();
            }
        }
    }
}