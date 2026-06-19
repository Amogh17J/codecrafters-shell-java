import java.util.*;
import java.io.*;

public class Main {
    public static void main(String[] args) throws Exception {

        Scanner sc = new Scanner(System.in);

        while (true) {
            System.out.print("$ ");

            String command = sc.nextLine();

            if (command.equals("exit 0") || command.equals("exit")) {
                break;
            }

            else if (command.startsWith("echo ")) {
                System.out.println(command.substring(5));
            }

            else if (command.startsWith("type ")) {

                String cmd = command.substring(5);

                if (cmd.equals("echo")
                        || cmd.equals("exit")
                        || cmd.equals("type")) {

                    System.out.println(cmd + " is a shell builtin");
                } else {

                    String path = System.getenv("PATH");
                    String[] dirs = path.split(":");

                    boolean found = false;

                    for (String dir : dirs) {
                        File file = new File(dir, cmd);

                        if (file.exists() && file.canExecute()) {
                            System.out.println(
                                    cmd + " is " + file.getAbsolutePath());

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
                    System.out.println(command + ": command not found");
                    continue;
                }

                List<String> commandList = new ArrayList<>();

for (String part : parts) {
    commandList.add(part);
}

                ProcessBuilder pb = new ProcessBuilder(commandList);

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