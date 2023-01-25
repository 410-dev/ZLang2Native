package me._410;

import me._410.struct.LibraryImportException;
import me._410.struct.ScriptFile;

import java.io.*;

public class Main {

    public static String libraryPath = "";

    public static final String extensionO = ".zsh";
    public static final String[] extensionI = {".zlang", ".zsh", ".sh", ".ebash"};
    public static final String extensionLib = ".ebsrc";
    public static final String extensionBase = ".zlangbase";
    public static final String VERSION = "1.0.0";

    public static boolean leaveComments = false;
    public static boolean leaveEmptyLines = false;
    public static boolean printStackTrace = false;

    private static String getParameter(String[] args, String parameter) {
        for (int i = 0; i < args.length; i++) {
            if (args[i].equals(parameter)) {
                if (i + 1 < args.length) {
                    return args[i + 1];
                }else{
                    return "";
                }
            }
        }
        return "";
    }

    private static boolean hasParameter(String[] args, String parameter) {
        for (String arg : args) {
            if (arg.equals(parameter)) {
                return true;
            }
        }
        return false;
    }


    private static String getOutputPath(String input, String output) {
        int differenceStartsAt = 0;
        int index = Math.max(input.length(), output.length());
        for(int i = 0; i < index; i++) {
            if (output.length() - 1 == i) {
                differenceStartsAt = output.length();
                break;
            }else if (input.length() - 1 == i) {
                differenceStartsAt = input.length();
                break;
            }

            if (output.charAt(i) != input.charAt(i)) {
                differenceStartsAt = i;
                break;
            }
        }

        String newOutput = output + input.substring(differenceStartsAt);

        File f = new File(input);
        if (f.isDirectory()) {
            newOutput = newOutput + "/";
        }else {
            newOutput = newOutput.substring(0, newOutput.lastIndexOf(".")) + extensionO;
        }
        return newOutput;
    }


    public static void buildRecursive(File dir, String output) throws IOException, LibraryImportException {
        File[] files = dir.listFiles();
        if (files == null) {
            System.out.println("Directory to translate is empty!");
            return;
        }

        for (File file : files) {
            if (file.getName().startsWith(".")) continue;
            if (file.isFile()) {

                // Check if file is a valid extension
                boolean validExtension = false;
                for (String extension : extensionI) {
                    if (file.getName().endsWith(extension)) {
                        validExtension = true;
                        break;
                    }
                }

                if (!validExtension) {
                    continue;
                }

                System.out.println("Translating file: " + file.getName());
                System.out.println("Parsing script...");
                ScriptFile sf = new ScriptFile(file.getPath(), false, "");

                System.out.println("Optimizing import...");
                sf.optimizeImports();
                System.out.println("Building as script...");
                sf.buildString();
                String outputPath = getOutputPath(file.getAbsolutePath(), output);

                System.out.println("Writing to: " + outputPath);

                BufferedWriter bw = new BufferedWriter(new FileWriter(outputPath));
                bw.write(sf.getBuiltString());
                bw.close();

                System.out.println("Translated " + sf.getPackageName());

                ScriptFile.imported.clear();
            }else{
                buildRecursive(file, output);
            }
        }
    }

    public static void mapDirectoryStructure(File sourceRoot, File outRoot, File current) {
        if (current.isFile()) {
            return;
        }
        File[] files = current.listFiles();
        if (files == null) {
            return;
        }
        for (File file : files) {
            if (file.isFile()) {
                String relativePath = file.getPath().replace(sourceRoot.getPath(), "");
                String outputPath = outRoot.getPath() + relativePath;
                File outputFile = new File(outputPath);
                outputFile.getParentFile().mkdirs();
            }else{
                mapDirectoryStructure(sourceRoot, outRoot, file);
            }
        }
    }

    public static void main(String[] args) throws LibraryImportException, FileNotFoundException {
        System.out.println("ZLang2Native Translator " + VERSION);

        // Required parameters: -i <input>
        // Optional parameters: -o <output> -l <library path> -c -e

        if (hasParameter(args, "help")) {
            System.out.println("Required: -i <input>");
            System.out.println("Optional: -o <output> -l <library path> -c -e");
            System.out.println("-c: Leave comments");
            System.out.println("-e: Leave empty lines");
            return;
        }

        // Get input file
        String input = getParameter(args, "-i");
        if (input.equals("")) {
            System.out.println("No input file specified. Usage: -i <input>");
            return;
        }

        // Get output file
        String output = getParameter(args, "-o");
        if (output.equals("")) {
            if (input.contains(".")) output = input.substring(0, input.lastIndexOf(".")) + extensionO;
            else output = input + "-out";
        }

        // Get library path
        libraryPath = getParameter(args, "-l");
        if (libraryPath.equals("")) {
            // Find from environment variable ZLANG_HOME
            libraryPath = System.getenv("ZLANG_HOME");
            if (libraryPath == null || libraryPath.equals("")) {
                System.out.println("No library path specified");
                return;
            }else{
                libraryPath = libraryPath.substring(0, libraryPath.length() - 1).substring(0, libraryPath.lastIndexOf("/"));
            }
        }

        // Get leave comments
        leaveComments = hasParameter(args, "-c");

        // Get leave empty lines
        leaveEmptyLines = hasParameter(args, "-e");

        // Get print stack trace
        printStackTrace = hasParameter(args, "-d");


        System.out.println("Starting translation with given configurations:");
        System.out.println("Input: " + input);
        System.out.println("Output: " + output);
        System.out.println("Library path: " + libraryPath);
        System.out.println("Leave comments: " + leaveComments);
        System.out.println("Leave empty lines: " + leaveEmptyLines);

        // If directory, then recursively import all files in directory
        File inputFile = new File(input);
        boolean build = false;
        try {
            if (inputFile.isDirectory()) {
                System.out.println("Translator detected that input is a directory. Translating all files in directory.");
                System.out.println("Mapping directory structure...");
                mapDirectoryStructure(inputFile, new File(output), inputFile);
                buildRecursive(inputFile, output);
            } else {
                System.out.println("Parsing script...");
                ScriptFile sf = new ScriptFile(input, false, "");
                System.out.println("Optimizing import...");
                sf.optimizeImports();
                System.out.println("Adding base components...");
                sf.buildBaseString();
                System.out.println("Building as script...");
                sf.buildString();
                System.out.println("Writing to: " + getOutputPath(input, output));
                BufferedWriter bw = new BufferedWriter(new FileWriter(getOutputPath(input, output)));
                bw.write(sf.getBuiltString());
                bw.close();
                System.out.println("Translated file: " + getOutputPath(input, output));
            }
            build = true;
        }catch (FileNotFoundException e) {
            if (printStackTrace) e.printStackTrace();
            System.out.println("ERROR: Required file not found");
        }catch (LibraryImportException e) {
            if (printStackTrace) e.printStackTrace();
            System.out.println("ERROR: " + e.getMessage());
        } catch (IOException e) {
            if (printStackTrace) e.printStackTrace();
            System.out.println("ERROR: Failed to read / write to file");
        }

        System.out.println("Build " + (build ? "succeed" : "failed") + ".");
    }
}
