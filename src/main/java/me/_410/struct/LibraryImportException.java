package me._410.struct;

public class LibraryImportException extends Exception {
    public LibraryImportException(String path, String line, String message) {
        super("Library import error in script '" + path + "' on line '" + line + "': " + message);
    }
}
