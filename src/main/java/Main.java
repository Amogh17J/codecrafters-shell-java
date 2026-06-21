import java.util.*;
import java.io.*;

public class Main {

    static class Job {
        int id;
        int pid;
        String cmd;
        Process process;
        boolean reportedDone;
        Job(int id, int pid, String cmd, Process process) {
            this.id = id;
            this.pid = pid;
            this.cmd = cmd;
            this.process = process;
            this.reportedDone = false;
        }
    }

    private static List<Job> jobList = new ArrayList<>();

    private static int getAvailableJobId() {
        if (jobList.isEmpty()) {
            return 1;
        }
        int maxId = 0;
        for (Job j : jobList) {
            if (j.id > maxId) {
                maxId = j.id;
            }
        }
        return maxId + 1;
    }

    private static void reapJobs() {
        List<Job> toRemove = new ArrayList<>();
        int currentSize = jobList.size();
        for (int i = 0; i < currentSize; i++) {
            Job j = jobList.get(i);
            if (!j.process.isAlive() && !j.reportedDone) {
                String marker = " ";
                if (i == currentSize - 1) {
                    marker = "+";
                } else if (i == currentSize - 2) {
                    marker = "-";
                }
                String statusField = String.format("%-24s", "Done");
                System.out.println("[" + j.id + "]" + marker + "  " + statusField + j.cmd);
                j.reportedDone = true;
                toRemove.add(j);
            }
        }
        jobList.removeAll(toRemove);
    }

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

    private static final String[] BUILTINS = { "echo", "exit", "type", "pwd", "cd", "jobs" };

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
        for (String cmd : BUILTINS) {
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

    private static void executeBuiltinWithStreams(List<String> args, InputStream customIn, PrintStream customOut) {
        if (args.isEmpty()) return;
        String name = args.get(0);

        if (name.equals("echo")) {
            StringBuilder sb = new StringBuilder();
            for (int i = 1; i < args.size(); i++) {
                if (i > 1) sb.append(" ");
                sb.append(args.get(i));
            }
            customOut.println(sb.toString());
        } 
        else if (name.equals("type")) {
            if (args.size() < 2) return;
            String target = args.get(1);
            boolean check = false;
            for (String b : BUILTINS) {
                if (b.equals(target)) {
                    check = true;
                    break;
                }
            }
            if (check) {
                customOut.println(target + " is a shell builtin");
            } else {
                String env = System.getenv("PATH");
                String[] dirs = env.split(":");
                boolean active = false;
                for (String d : dirs) {
                    File f = new File(d, target);
                    if (f.isFile() && f.canExecute()) {
                        customOut.println(target + " is " + f.getAbsolutePath());
                        active = true;
                        break;
                    }
                }
                if (!active) {
                    customOut.println(target + ": not found");
                }
            }
        }
        else if (name.equals("pwd")) {
            customOut.println(System.getProperty("user.dir"));
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
                reapJobs();

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

                boolean bgFlag = false;
                if (parts.get(parts.size() - 1).equals("&")) {
                    bgFlag = true;
                    parts.remove(parts.size() - 1);
                }

                if (parts.isEmpty()) {
                    continue;
                }

                if (parts.contains("|")) {
                    List<List<String>> cmdStages = new ArrayList<>();
                    List<String> currentStage = new ArrayList<>();

                    for (String s : parts) {
                        if (s.equals("|")) {
                            if (!currentStage.isEmpty()) {
                                cmdStages.add(currentStage);
                                currentStage = new ArrayList<>();
                            }
                        } else {
                            currentStage.add(s);
                        }
                    }
                    if (!currentStage.isEmpty()) {
                        cmdStages.add(currentStage);
                    }

                    if (cmdStages.size() == 2) {
                        List<String> list1 = cmdStages.get(0);
                        List<String> list2 = cmdStages.get(1);

                        boolean b1 = false;
                        for (String b : BUILTINS) {
                            if (b.equals(list1.get(0))) b1 = true;
                        }

                        boolean b2 = false;
                        for (String b : BUILTINS) {
                            if (b.equals(list2.get(0))) b2 = true;
                        }

                        if (b1 && !b2) {
                            PipedOutputStream po = new PipedOutputStream();
                            PipedInputStream pi = new PipedInputStream(po);
                            PrintStream ps = new PrintStream(po);

                            ProcessBuilder builder2 = new ProcessBuilder(list2).directory(currentDirectory);
                            builder2.redirectInput(ProcessBuilder.Redirect.PIPE);
                            builder2.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                            builder2.redirectError(ProcessBuilder.Redirect.INHERIT);

                            Process p2 = builder2.start();

                            executeBuiltinWithStreams(list1, System.in, ps);
                            ps.close();
                            po.close();

                            try (InputStream childIn = pi; OutputStream childOut = p2.getOutputStream()) {
                                byte[] buffer = new byte[1024];
                                int bytesRead;
                                while ((bytesRead = childIn.read(buffer)) != -1) {
                                    childOut.write(buffer, 0, bytesRead);
                                }
                            } catch (IOException e) {
                            }

                            p2.getOutputStream().close();
                            p2.waitFor();
                            continue;
                        } 
                        else if (!b1 && b2) {
                            ProcessBuilder builder1 = new ProcessBuilder(list1).directory(currentDirectory);
                            builder1.redirectInput(ProcessBuilder.Redirect.INHERIT);
                            builder1.redirectOutput(ProcessBuilder.Redirect.PIPE);
                            builder1.redirectError(ProcessBuilder.Redirect.INHERIT);

                            Process p1 = builder1.start();

                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            PrintStream ps = new PrintStream(baos);

                            try (InputStream childOut = p1.getInputStream()) {
                                byte[] buffer = new byte[1024];
                                int bytesRead;
                                while ((bytesRead = childOut.read(buffer)) != -1) {
                                }
                            }

                            p1.waitFor();
                            executeBuiltinWithStreams(list2, System.in, System.out);
                            continue;
                        }
                        else if (b1 && b2) {
                            ByteArrayOutputStream baos = new ByteArrayOutputStream();
                            PrintStream ps = new PrintStream(baos);
                            executeBuiltinWithStreams(list1, System.in, ps);
                            executeBuiltinWithStreams(list2, System.in, System.out);
                            continue;
                        }
                    }

                    List<ProcessBuilder> builders = new ArrayList<>();
                    for (int i = 0; i < cmdStages.size(); i++) {
                        ProcessBuilder pb = new ProcessBuilder(cmdStages.get(i)).directory(currentDirectory);
                        if (i == cmdStages.size() - 1) {
                            pb.redirectOutput(ProcessBuilder.Redirect.INHERIT);
                            pb.redirectError(ProcessBuilder.Redirect.INHERIT);
                        }
                        builders.add(pb);
                    }

                    List<Process> allP = ProcessBuilder.startPipeline(builders);
                    Process lastP = allP.get(allP.size() - 1);

                    if (bgFlag) {
                        int jobId = getAvailableJobId();
                        String fullCmdStr = String.join(" ", parts);
                        jobList.add(new Job(jobId, (int) lastP.pid(), fullCmdStr, lastP));
                        System.out.println("[" + jobId + "] " + lastP.pid());
                        System.out.flush();
                    } else {
                        lastP.waitFor();
                    }
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
                    List<Job> toRemove = new ArrayList<>();
                    int totalJobs = jobList.size();
                    for (int i = 0; i < totalJobs; i++) {
                        Job j = jobList.get(i);
                        String marker = " ";
                        if (i == totalJobs - 1) {
                            marker = "+";
                        } else if (i == totalJobs - 2) {
                            marker = "-";
                        }

                        if (j.process.isAlive()) {
                            String statusField = String.format("%-24s", "Running");
                            System.out.println("[" + j.id + "]" + marker + "  " + statusField + j.cmd + " &");
                        } else {
                            String statusField = String.format("%-24s", "Done");
                            System.out.println("[" + j.id + "]" + marker + "  " + statusField + j.cmd);
                            toRemove.add(j);
                        }
                    }
                    jobList.removeAll(toRemove);
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
                    String outFileStr = null;
                    String errFileStr = null;
                    String redirType = null;
                    boolean appendFlag = false;

                    List<String> runArgs = new ArrayList<>(parts);

                    for (int i = 0; i < runArgs.size(); i++) {
                        String tok = runArgs.get(i);
                        if (tok.equals(">") || tok.equals("1>") || tok.equals(">>") || tok.equals("1>>")) {
                            if (i + 1 < runArgs.size()) {
                                outFileStr = runArgs.get(i + 1);
                            }
                            redirType = "stdout";
                            appendFlag = tok.equals(">>") || tok.equals("1>>");
                            runArgs = new ArrayList<>(runArgs.subList(0, i));
                            break;
                        }
                        if (tok.equals("2>") || tok.equals("2>>")) {
                            if (i + 1 < runArgs.size()) {
                                errFileStr = runArgs.get(i + 1);
                            }
                            redirType = "stderr";
                            appendFlag = tok.equals("2>>");
                            runArgs = new ArrayList<>(runArgs.subList(0, i));
                            break;
                        }
                    }

                    if (runArgs.isEmpty()) {
                        continue;
                    }

                    String runCmd = runArgs.get(0);
                    String pathEnv = System.getenv("PATH");
                    String[] dirs = pathEnv.split(":");
                    File exeFile = null;

                    for (String dir : dirs) {
                        File file = new File(dir, runCmd);
                        if (file.isFile() && file.canExecute()) {
                            exeFile = file;
                            break;
                        }
                    }

                    if (exeFile == null) {
                        System.out.println(runCmd + ": command not found");
                        continue;
                    }

                    ProcessBuilder pb = new ProcessBuilder(runArgs);
                    pb.directory(currentDirectory);

                    if (bgFlag) {
                        if (redirType == null) {
                            pb.inheritIO();
                        }
                        Process p = pb.start();
                        
                        int jobId = getAvailableJobId();
                        String fullCmdStr = String.join(" ", runArgs);
                        jobList.add(new Job(jobId, (int) p.pid(), fullCmdStr, p));
                        System.out.println("[" + jobId + "] " + p.pid());
                        System.out.flush();
                        continue;
                    }

                    Process p = pb.start();

                    BufferedReader outRead = new BufferedReader(
                            new InputStreamReader(p.getInputStream()));
                    BufferedReader errRead = new BufferedReader(
                            new InputStreamReader(p.getErrorStream()));

                    if ("stdout".equals(redirType)) {
                        File outFile;
                        if (new File(outFileStr).isAbsolute()) {
                            outFile = new File(outFileStr);
                        } else {
                            outFile = new File(currentDirectory, outFileStr);
                        }

                        File parent = outFile.getParentFile();
                        if (parent != null) {
                            parent.mkdirs();
                        }

                        try (BufferedWriter writer = new BufferedWriter(new FileWriter(outFile, appendFlag))) {
                            String line;
                            while ((line = outRead.readLine()) != null) {
                                writer.write(line);
                                writer.newLine();
                            }
                        }
                    } else {
                        String line;
                        while ((line = outRead.readLine()) != null) {
                            System.out.println(line);
                        }
                    }
                    outRead.close();

                    if ("stderr".equals(redirType)) {
                        File errFile;
                        if (new File(errFileStr).isAbsolute()) {
                            errFile = new File(errFileStr);
                        } else {
                            errFile = new File(currentDirectory, errFileStr);
                        }

                        File parent = errFile.getParentFile();
                        if (parent != null) {
                            parent.mkdirs();
                        }

                        try (BufferedWriter errWriter = new BufferedWriter(new FileWriter(errFile, appendFlag))) {
                            String errLine;
                            while ((errLine = errRead.readLine()) != null) {
                                errWriter.write(errLine);
                                errWriter.newLine();
                            }
                        }
                    } else {
                        String errLine;
                        while ((errLine = errRead.readLine()) != null) {
                            System.err.println(errLine);
                        }
                    }
                    errRead.close();

                    p.waitFor();
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