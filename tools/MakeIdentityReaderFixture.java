import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.security.spec.*;
import java.time.LocalDate;
import java.util.Arrays;
import javax.crypto.*;
import javax.crypto.spec.*;

/** Generates only invented data to validate the local reader end to end. */
public final class MakeIdentityReaderFixture {
    public static void main(String[] args) throws Exception {
        if (args.length != 2) throw new IllegalArgumentException();
        Path output = Path.of(args[1]);
        Files.createDirectory(output);
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        try {
            PublicKey publicKey = KeyFactory.getInstance("RSA")
                .generatePublic(new X509EncodedKeySpec(Files.readAllBytes(Path.of(args[0]))));
            Cipher wrap = Cipher.getInstance("RSA/ECB/OAEPWithSHA-256AndMGF1Padding");
            wrap.init(Cipher.ENCRYPT_MODE, publicKey,
                new OAEPParameterSpec("SHA-256", "MGF1", MGF1ParameterSpec.SHA256, PSource.PSpecified.DEFAULT));
            Files.write(output.resolve("recovery-key.rsa"), wrap.doFinal(key), StandardOpenOption.CREATE_NEW);
            String json = fixture();
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] derived = hmac.doFinal("bitavida-identity-diagnostics-v1".getBytes(StandardCharsets.US_ASCII));
            try {
                byte[] data = json.getBytes(StandardCharsets.UTF_8);
                byte[] prefix = new byte[8];
                new SecureRandom().nextBytes(prefix);
                byte[] magic = {0x42, 0x56, 0x41, 0x32};
                try (DataOutputStream out = new DataOutputStream(
                        Files.newOutputStream(output.resolve("identity-diagnostics.bva2"), StandardOpenOption.CREATE_NEW))) {
                    out.write(magic);
                    out.write(prefix);
                    int counter = 0;
                    for (int offset = 0; offset < data.length; offset += 83) {
                        int n = Math.min(83, data.length - offset);
                        frame(out, derived, magic, prefix, counter++, Arrays.copyOfRange(data, offset, offset + n));
                    }
                    frame(out, derived, magic, prefix, counter, new byte[0]);
                }
                Arrays.fill(data, (byte) 0);
            } finally { Arrays.fill(derived, (byte) 0); }
        } finally { Arrays.fill(key, (byte) 0); }
        System.out.println("Synthetic encrypted reader fixture created.");
    }
    private static void frame(DataOutputStream out, byte[] key, byte[] magic, byte[] prefix,
                              int counter, byte[] payload) throws Exception {
        int n = payload.length;
        byte[] nonce = ByteBuffer.allocate(12).put(prefix).putInt(counter).array();
        byte[] aad = ByteBuffer.allocate(20).put(magic).put(prefix).putInt(counter).putInt(n).array();
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
        cipher.updateAAD(aad);
        out.writeInt(n);
        out.write(cipher.doFinal(payload));
        Arrays.fill(payload, (byte) 0);
    }
    private static String fixture() {
        long testDay = LocalDate.of(2026, 11, 12).toEpochDay();
        String empty = section("[]");
        String history = """
            [
              {"intakeRecordId":"r-fake","revisionNumber":1,"outcome":"COMPLETED",
               "scheduledEpochDay":%d,"scheduledMinute":1290,"actualMinute":1140,
               "scheduledOccurrenceId":"o-fake"},
              {"intakeRecordId":"r-fake","revisionNumber":2,"outcome":"PARTIAL",
               "scheduledEpochDay":%d,"scheduledMinute":1290,"actualMinute":1140,
               "scheduledOccurrenceId":"o-fake"}
            ]
            """.formatted(testDay, testDay);
        String plan = """
            {
              "plan":%s,
              "plan_revisions":%s,
              "administration_schedules":%s,
              "intake_records":%s,
              "intake_revisions":%s
            }
            """.formatted(
                section(""" 
                    [{"id":"p-fake","medicationSeriesId":"s-fake"}]
                    """),
                empty,
                empty,
                section("""
                    [{"id":"r-fake","currentRevisionNumber":2}]
                    """),
                section(history)
            );
        return """
            {
              "format":"bitavida-identity-diagnostics-v1",
              "requested_targets":[
                {"requested_label":"Test","plans":[%s]},
                {"requested_label":"Pantoprazol","plans":[]},
                {"requested_label":"Loperamkda","plans":[]}
              ]
            }
            """.formatted(plan);
    }
    private static String section(String rows) {
        return """
            {"filter_available":true,"unavailable_columns":[],"rows":%s}
            """.formatted(rows);
    }
}
