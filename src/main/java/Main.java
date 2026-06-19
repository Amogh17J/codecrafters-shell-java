import java.util.*;
import java.io.*;

public class Main {

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

                File target = new File(path);

                if (target.exists() && target.isDirectory()) {
                    currentDirectory = target;
                } else {
                    System.out.println(
                            "cd: " + path + ": No such file or directory");
                }
            }

            else if (command.startsWith("echo ")) {
                System.out.println(command.substring(5));
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

                        if (file.exists() && file.canExecute()) {

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

                String[] parts = command.split("\\s+");

                String cmd = parts[0];

                String path = System.getenv("PATH");
                String[] dirs = path.split(":");

                File executable = null;

                for (String dir : dirs) {

                    File file = new File(dir, cmd);

                    if (file.exists() && file.canExecute()) {
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

                for (String part : parts) {
                    commandList.add(part);
                }

                ProcessBuilder pb =
                        new ProcessBuilder(commandList);

                pb.directory(currentDirectory);

                Process process = pb.start();

                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        process.getInputStream()));

                String line;

                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }

                process.waitFor();
            }
        }
    }
}
