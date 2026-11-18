import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.*;
import java.util.Base64;

/** Generates local credentials without putting private material in console output. */
class GenerateKeys {
    public static void main(String[] args) throws Exception {
        Path dir = Path.of("secrets");
        Files.createDirectories(dir);
        Path privateKey = dir.resolve("private.pem");
        Path publicKey = dir.resolve("public.pem");
        if (Files.exists(privateKey) != Files.exists(publicKey)) {
            throw new IllegalStateException("Incomplete key pair. Restore the matching key before running setup.");
        }
        if (!Files.exists(privateKey)) {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            KeyPair keys = generator.generateKeyPair();
            writePem(privateKey, "PRIVATE KEY", keys.getPrivate().getEncoded());
            writePem(publicKey, "PUBLIC KEY", keys.getPublic().getEncoded());
            if (Files.getFileStore(dir).supportsFileAttributeView("posix")) {
                Files.setPosixFilePermissions(privateKey, PosixFilePermissions.fromString("rw-------"));
            }
        }
        Path env = Path.of(".env");
        if (!Files.exists(env)) {
            Files.writeString(env, "IDENTITY_DB_PASSWORD=" + secret() + "\nCOURSE_DB_PASSWORD=" + secret()
                    + "\nGRADE_DB_PASSWORD=" + secret() + "\nDEMO_PASSWORD=" + secret() + "\n", StandardCharsets.UTF_8, StandardOpenOption.CREATE_NEW);
            if (Files.getFileStore(env).supportsFileAttributeView("posix")) Files.setPosixFilePermissions(env, PosixFilePermissions.fromString("rw-------"));
        }
        System.out.println("Local keys and .env are ready. Demo credentials remain in .env; do not commit that file.");
    }

    static String secret() {
        byte[] bytes = new byte[24];
        new SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static void writePem(Path path, String label, byte[] data) throws Exception {
        Files.writeString(path, "-----BEGIN " + label + "-----\n"
                + Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(data)
                + "\n-----END " + label + "-----\n", StandardCharsets.US_ASCII, StandardOpenOption.CREATE_NEW);
    }
}
