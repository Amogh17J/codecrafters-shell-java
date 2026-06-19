import java.util.Scanner;
import java.io.File;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class Main {
    public static void main(String[] args) {

        Scanner sc = new Scanner(System.in);

        while (true) {

            System.out.print("$ ");

            String command = sc.nextLine();

            // exit builtin
            if (command.equals("exit 0") || command.equals("exit")) {
                break;
            }

            // echo builtin
            else if (command.startsWith("echo ")) {
                System.out.println(command.substring(5));
            }

            // type builtin
            else if (command.startsWith("type ")) {

                String cmd = command.substring(5);

                // Check builtins first
                if (cmd.equals("echo")
                        || cmd.equals("exit")
                        || cmd.equals("type")) {

                    System.out.println(cmd + " is a shell builtin");
                }

                else {

                    String path = System.getenv("PATH");
                    String[] directories = path.split(":");

                    boolean found = false;

                    for (String dir : directories) {

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

            // Invalid command
            else {

    String[] parts = command.split("\\s+");
    String cmd = parts[0];

    String path = System.getenv("PATH");
    String[] directories = path.split(":");

    boolean found = false;

    for (String dir : directories) {

        File file = new File(dir, cmd);

        if (file.exists() && file.canExecute()) {

            found = true;

            try {

                java.util.List<String> commandList =
                        new java.util.ArrayList<>();

                commandList.add(file.getAbsolutePath());

                for (int i = 1; i < parts.length; i++) {
                    commandList.add(parts[i]);
                }

                ProcessBuilder pb =
                        new ProcessBuilder(commandList);

                Process process = pb.start();

                java.io.BufferedReader reader =
                        new java.io.BufferedReader(
                                new java.io.InputStreamReader(
                                        process.getInputStream()));

                String line;

                while ((line = reader.readLine()) != null) {
                    System.out.println(line);
                }

                process.waitFor();

            } catch (Exception e) {
                e.printStackTrace();
            }

            break;
        }
    }

    if (!found) {
        System.out.println(command + ": command not found");
    }
}

        }
    }
}