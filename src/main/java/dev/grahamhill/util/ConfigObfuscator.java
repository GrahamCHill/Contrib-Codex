package dev.grahamhill.util;

import java.util.Base64;

public class ConfigObfuscator {
    private static final String KEY = "ContribCodexSecretKey"; // Simple obfuscation key

    public static String obfuscate(String input) {
        if (input == null) return null;
        byte[] bytes = input.getBytes();
        byte[] keyBytes = KEY.getBytes();
        byte[] result = new byte[bytes.length];
        for (int i = 0; i < bytes.length; i++) {
            result[i] = (byte) (bytes[i] ^ keyBytes[i % keyBytes.length]);
        }
        return Base64.getEncoder().encodeToString(result);
    }

    public static String deobfuscate(String input) {
        if (input == null) return null;
        try {
            byte[] bytes = Base64.getDecoder().decode(input);
            byte[] keyBytes = KEY.getBytes();
            byte[] result = new byte[bytes.length];
            for (int i = 0; i < bytes.length; i++) {
                result[i] = (byte) (bytes[i] ^ keyBytes[i % keyBytes.length]);
            }
            return new String(result);
        } catch (Exception e) {
            // If it's not base64 or decryption fails, return as is (might be plain text)
            return input;
        }
    }

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Usage: ConfigObfuscator <obfuscate|deobfuscate> <file_path>");
            return;
        }

        String mode = args[0];
        java.nio.file.Path path = java.nio.file.Paths.get(args[1]);
        String content = java.nio.file.Files.readString(path);

        if ("obfuscate".equals(mode)) {
            String obfuscated = obfuscate(content);
            java.nio.file.Files.writeString(path, obfuscated);
            System.out.println("File obfuscated successfully.");
        } else if ("deobfuscate".equals(mode)) {
            String deobfuscated = deobfuscate(content);
            java.nio.file.Files.writeString(path, deobfuscated);
            System.out.println("File deobfuscated successfully.");
        }
    }
}
