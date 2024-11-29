import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Fuzzer {

    private static final String SYMBOLS = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789!@#$%^&*()_+[]{}|;:,.<>?/";

    public static void main(String[] args) {
        if (args.length != 1) {
            System.err.println("Usage: java Fuzzer.java \"<command_to_fuzz>\"");
            System.exit(1);
        }
        String commandToFuzz = args[0];
        String workingDirectory = "./";

        if (!Files.exists(Paths.get(workingDirectory, commandToFuzz))) {
            throw new RuntimeException("Could not find command '%s'.".formatted(commandToFuzz));
        }

        String seedInput = "<html a=\"value\">...</html>";
        Random random = new Random();

        List<List<String>> testInputs = new ArrayList<>();
        testInputs.add(generateRandomStrings(random, 1, 1)); // Test 1: Random strings
        testInputs.add(generateRandomIntegers(random, 1));   // Test 2: Random integers
        testInputs.add(generateRandomSpecialChars(random, 1)); // Test 3: Random special characters
        testInputs.add(generateRandomCombinations(random, 1, 5)); // Test 4: Random combinations
        testInputs.add(generateRandomCombinations(random, 1, 250)); // Test 5: 250-character combinations

        // Generate all mutated inputs by cycling through test cases for each character in the seed input
        List<String> allInputs = generateSequentialMutatedInputs(seedInput, testInputs);

        ProcessBuilder builder = getProcessBuilderForCommand(commandToFuzz, workingDirectory);
        System.out.printf("Command: %s\n", builder.command());

        runCommand(builder, allInputs, 150); // Maximum of 150 tests
    }

    private static ProcessBuilder getProcessBuilderForCommand(String command, String workingDirectory) {
        ProcessBuilder builder = new ProcessBuilder();
        boolean isWindows = System.getProperty("os.name").toLowerCase().startsWith("windows");
        if (isWindows) {
            builder.command("cmd.exe", "/c", command);
        } else {
            builder.command("sh", "-c", command);
        }
        builder.directory(new File(workingDirectory));
        builder.redirectErrorStream(true);
        return builder;
    }

    private static void runCommand(ProcessBuilder builder, List<String> inputs, int maxTests) {
        int testNumber = 1;

        for (String input : inputs) {
            if (testNumber > maxTests) break;

            System.out.printf("Test %d:\n", testNumber++);
            try {
                Process process = builder.start();

                try (BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                    writer.write(input);
                    writer.flush();
                }

                String output = readStreamIntoString(process.getInputStream());
                String error = readStreamIntoString(process.getErrorStream());

                System.out.printf("Input: %s\nOutput: %s\nError: %s\n", input, output, error);

                // Wait for the process to complete and get the exit code
                int exitCode = process.waitFor();
                System.out.printf("Exit code: %d\n\n", exitCode);

                // Terminate program if the exit code is not zero
                if (exitCode != 0) {
                    System.err.printf("Non-zero exit code detected for input '%s'. Terminating program.\n", input);
                    System.exit(exitCode);
                }

            } catch (IOException | InterruptedException e) {
                System.err.printf("Error running command with input '%s': %s\n", input, e.getMessage());
                System.exit(1);
            }
        }

        System.out.println("All tests passed with exit code 0.");
    }

    private static String readStreamIntoString(InputStream inputStream) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
            return reader.lines()
                    .map(line -> line + System.lineSeparator())
                    .collect(Collectors.joining());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static List<String> generateSequentialMutatedInputs(String seedInput, List<List<String>> testInputs) {
        List<String> mutatedInputs = new ArrayList<>();

        for (int i = 0; i < seedInput.length(); i++) {
            for (List<String> testCaseInputs : testInputs) {
                for (String replacement : testCaseInputs) {
                    String mutatedInput = seedInput.substring(0, i) + replacement + seedInput.substring(i + 1);
                    mutatedInputs.add(mutatedInput);
                }
            }
        }

        return mutatedInputs;
    }

    private static List<String> generateRandomStrings(Random random, int count, int length) {
        return generateRandomCombinations(random, count, length, "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz");
    }

    private static List<String> generateRandomIntegers(Random random, int count) {
        return generateRandomCombinations(random, count, 1, "0123456789");
    }

    private static List<String> generateRandomSpecialChars(Random random, int count) {
        return generateRandomCombinations(random, count, 1, "!@#$%^&*()_+[]{}|;:,.<>?/");
    }

    private static List<String> generateRandomCombinations(Random random, int count, int length) {
        return generateRandomCombinations(random, count, length, SYMBOLS);
    }

    private static List<String> generateRandomCombinations(Random random, int count, int length, String charset) {
        return Stream.generate(() -> {
            char[] chars = new char[length];
            for (int i = 0; i < length; i++) {
                chars[i] = charset.charAt(random.nextInt(charset.length()));
            }
            return new String(chars);
        }).limit(count).collect(Collectors.toList());
    }
}