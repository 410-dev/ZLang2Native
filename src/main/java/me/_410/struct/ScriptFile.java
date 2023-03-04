package me._410.struct;

import lombok.Getter;
import me._410.Keywords;
import me._410.Main;

import java.io.*;
import java.util.ArrayList;

@Getter
public class ScriptFile {
    public static ArrayList<ScriptFile> imported = new ArrayList<>();

    private String path;
    private ArrayList<String> lines = new ArrayList<>();
    private String builtString = "";

    private String requiredVersion = "selected";
    private String libraryPath = Main.libraryPath + "/" + requiredVersion + "/";
    private boolean isLibraryFile;
    private String packageName = "";

    public ScriptFile(String path, boolean isLibraryFile, String packageName) throws IOException, LibraryImportException {
        this.path = path;
        this.isLibraryFile = isLibraryFile;

        if (isLibraryFile) this.packageName = packageName;
        else this.packageName = path;

        System.out.println("Script '" + this.packageName + "' is being parsed as " + (isLibraryFile ? "library file" : "standalone script file"));

        // Read file and add to lines
        BufferedReader reader = new BufferedReader(new FileReader(path));
        String line;
        while ((line = reader.readLine()) != null) {
            lines.add(line);
        }
        reader.close();
        System.out.println("Total " + lines.size() + " lines read from script '" + this.packageName + "'");

        // Scan lines
        for (int i = 0; i < lines.size(); i++) {
            line = lines.get(i);

            if (line.contains("export -f") && !line.startsWith("#") && !Main.hasParameter(null, "-b")) {
                throw new LibraryImportException(path, line, "Syntax 'export -f' is not supported");
            }

            // If line is source $ZLANG_HOME/zlang-linker $ZLANG_HOME, then remove
            if (line.replaceAll("\"", "").replaceAll("'", "").equals("source $ZLANG_HOME/zlang-linker $ZLANG_HOME")) {
                System.out.println("Removing zlang linkage...");
                lines.set(i, "");
                continue;
            }

            // If line is @translate, remove line
            if (line.startsWith(Keywords.TRANSLATE)) {
                System.out.println("Found translation mark.");
                lines.set(i, "");
                continue;
            }

            // If line is @require, set required version and remove line
            if (line.startsWith(Keywords.REQUIRE)) {
                requiredVersion = line.replace(Keywords.REQUIRE, "").trim();
                System.out.println("Library version " + requiredVersion + " is required. Configuration will be updated.");
                System.out.print("Library path switched from " + libraryPath + " to ");
                libraryPath = Main.libraryPath + "/" + requiredVersion + "/";
                System.out.println(libraryPath + ".");
                lines.set(i, "");
                continue;
            }

            // If line is import, then read library, add to imported and remove line
            if (line.startsWith(Keywords.IMPORT) && !line.endsWith(Keywords.IMPORT)) {
                String name = line.substring(Keywords.IMPORT.length() + 1);
                String importPath = line.substring(Keywords.IMPORT.length() + 1);
                System.out.println("Script is importing " + importPath);
                importPath = libraryPath + "lib/" + importPath;

                // If import path is a directory, then recursively import all files in directory
                File importFile = new File(importPath);
                if (importFile.isDirectory()) {
                    System.out.println("Importing as package.");
                    File[] files = importFile.listFiles();
                    if (files == null) {
                        throw new LibraryImportException(this.path, String.valueOf(i), "Directory to import is empty");
                    }

                    for (File file : files) {
                        if (file.isFile()) {

                            if (!file.getAbsolutePath().endsWith(Main.extensionLib)) continue;

                            System.out.println("Importing: " + name);
                            imported.add(new ScriptFile(file.getPath(), true, name));
                        }
                    }
                } else {
                    System.out.println("Importing as a single module.");
                    imported.add(new ScriptFile(importPath + Main.extensionLib, true, name));
                    System.out.println("Imported " + name + " as a single module.");
                }

                lines.set(i, "");
            }
        }
    }

    public void optimizeImports() {
        // Remove duplicated imports
        for (int i = 0; i < imported.size(); i++) {
            ScriptFile sf = imported.get(i);
            for (int j = i + 1; j < imported.size(); j++) {
                ScriptFile sf2 = imported.get(j);
                if (sf.equals(sf2)) {
                    System.out.println("Removing duplicated import: " + sf.getPackageName());
                    imported.remove(j);
                    j--;
                }
            }
        }
    }

    public void buildBaseString() throws LibraryImportException, IOException {
        StringBuilder sb = new StringBuilder();

        // Load all base elements
        System.out.println("Loading base components...");
        File lib = new File(libraryPath + "/base");
        File[] files = lib.listFiles();
        if (files == null) {
            throw new LibraryImportException(this.path, "COMPILER", "Base library is empty");
        }

        String[] excluded = {"import", "translatorannotation"};
        for (File file : files) {
            if (file.isFile()) {

                boolean skip = false;
                for (String s : excluded) {
                    if (file.getName().contains(s)) {
                        skip = true;
                        break;
                    }
                }

                if (!file.getAbsolutePath().endsWith(Main.extensionBase)) skip = true;

                if (skip) continue;

                BufferedReader reader = new BufferedReader(new FileReader(file.getAbsolutePath()));
                String s = reader.readLine();
                while (s != null) {
                    if (s.trim().startsWith("#") || s.trim().replaceAll(" ", "").equals("")) {
                        s = reader.readLine();
                        continue;
                    }
                    sb.append(s);
                    sb.append("\n");
                    s = reader.readLine();
                }
                reader.close();
            }
        }
        System.out.println("Loaded base components.");
        builtString = sb.toString();
    }

    public void buildString() throws LibraryImportException, IOException {

        StringBuilder sb = new StringBuilder();

        if (!this.isLibraryFile) {
            for (ScriptFile sf : imported) {
                System.out.println("Building dependency string for " + sf.getPackageName());
                sf.buildString();
                sb.append(sf.getBuiltString());
                sb.append("\n");
                System.out.println("Built dependency string for " + sf.getPackageName());
            }
        }else{
            System.out.println("Skipped dependency string for " + this.getPackageName());
        }

        if (Main.leaveComments) System.out.println("Warning: Comments will be left in the final script.");
        if (Main.leaveEmptyLines) System.out.println("Warning: Empty lines will be left in the final script.");

        for (String line : lines) {
            sb.append(line);
            sb.append("\n");
        }

        this.builtString += sb.toString();

        String[] lines = this.builtString.split("\n");
        builtString = "";
        StringBuilder sb2 = new StringBuilder();
        if (Main.hasParameter(null, "-b")) sb2.append("#!/bin/bash");
        else sb2.append("#!/bin/zsh");
        sb2.append("\n");
        for (String line : lines) {
            boolean isLineEmpty = line.trim().replaceAll(" ", "").equals("");
            if (this.isLibraryFile) {
                if (line.trim().startsWith("#")) continue;
                if (isLineEmpty) continue;
            }else {
                if (line.trim().startsWith("#!")) continue;
                if (line.trim().startsWith("#") && !Main.leaveComments) continue;
                if (isLineEmpty && !Main.leaveEmptyLines) continue;
            }
            sb2.append(line);
            sb2.append("\n");
        }
        this.builtString = sb2.toString();

        System.out.println("Build complete for " + this.getPackageName());
    }

    public boolean equals(Object o) {
        if (o instanceof ScriptFile sf) {
            return sf.getPath().equals(this.getPath());
        }
        return false;
    }
}
