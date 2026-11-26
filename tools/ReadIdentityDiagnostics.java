import java.io.*;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.util.*;
import javax.crypto.Cipher;
import javax.crypto.Mac;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

/**
 * Local-only reader. The 32-byte backup key arrives through stdin, never argv.
 * Plaintext JSON exists only in this short-lived process; output is aggregate
 * identity evidence, not names of patients, IDs, dates, quantities or notes.
 */
public final class ReadIdentityDiagnostics {
    private static final byte[] MAGIC = {0x42, 0x56, 0x41, 0x32};
    private static final int MAX_PLAINTEXT = 64 * 1024 * 1024;

    public static void main(String[] args) {
        byte[] backupKey = new byte[32];
        byte[] derived = null;
        byte[] plaintext = null;
        String stage = "entrada";
        try {
            if (args.length != 2 || !args[1].matches("[0-9a-f]{64}")) throw new Exception();
            Path file = Path.of(args[0]);
            if (!digest(file).equals(args[1])) throw new Exception();
            stage = "clave";
            DataInputStream secretInput = new DataInputStream(System.in);
            secretInput.readFully(backupKey);
            if (secretInput.read() != -1) throw new Exception();
            Mac hmac = Mac.getInstance("HmacSHA256");
            hmac.init(new SecretKeySpec(backupKey, "HmacSHA256"));
            derived = hmac.doFinal("bitavida-identity-diagnostics-v1".getBytes(StandardCharsets.US_ASCII));
            stage = "descifrado";
            plaintext = decrypt(file, derived);
            if (!digest(file).equals(args[1])) throw new Exception();
            stage = "lectura";
            Object parsed = new JsonParser(new String(plaintext, StandardCharsets.UTF_8)).parse();
            Map<?, ?> root = asMap(parsed);
            if (!"bitavida-identity-diagnostics-v1".equals(root.get("format"))) throw new Exception();
            stage = "agregacion";
            Object schema = root.get("schema_version");
            Object violations = root.get("foreign_key_violations");
            if (schema instanceof Number && violations instanceof Number) {
                System.out.println("Esquema=" + ((Number) schema).longValue()
                        + ", violaciones foreign_key_check=" + ((Number) violations).longValue() + ".");
            } else {
                System.out.println("Esquema o foreign_key_check no disponible en el diagnóstico.");
            }
            summarize(asList(root.get("requested_targets")));
        } catch (Throwable ignored) {
            System.err.println("Fallo del lector en etapa " + stage + ".");
            System.exit(1);
        } finally {
            Arrays.fill(backupKey, (byte) 0);
            if (derived != null) Arrays.fill(derived, (byte) 0);
            if (plaintext != null) Arrays.fill(plaintext, (byte) 0);
        }
    }

    private static byte[] decrypt(Path file, byte[] key) throws Exception {
        try (DataInputStream in = new DataInputStream(new BufferedInputStream(Files.newInputStream(file)));
             ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] magic = new byte[4];
            byte[] prefix = new byte[8];
            in.readFully(magic);
            in.readFully(prefix);
            if (!Arrays.equals(magic, MAGIC)) throw new Exception();
            int counter = 0;
            while (true) {
                int size = in.readInt();
                if (size < 0 || size > 65536 || counter == Integer.MAX_VALUE) throw new Exception();
                byte[] ciphertext = new byte[size + 16];
                in.readFully(ciphertext);
                byte[] nonce = ByteBuffer.allocate(12).put(prefix).putInt(counter).array();
                byte[] aad = ByteBuffer.allocate(20).put(MAGIC).put(prefix).putInt(counter).putInt(size).array();
                Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
                cipher.init(Cipher.DECRYPT_MODE, new SecretKeySpec(key, "AES"), new GCMParameterSpec(128, nonce));
                cipher.updateAAD(aad);
                byte[] frame = cipher.doFinal(ciphertext);
                Arrays.fill(ciphertext, (byte) 0);
                Arrays.fill(nonce, (byte) 0);
                Arrays.fill(aad, (byte) 0);
                if (frame.length != size) throw new Exception();
                if (size == 0) {
                    if (in.read() != -1) throw new Exception();
                    Arrays.fill(frame, (byte) 0);
                    return out.toByteArray();
                }
                if ((long) out.size() + size > MAX_PLAINTEXT) throw new Exception();
                out.write(frame);
                Arrays.fill(frame, (byte) 0);
                counter++;
            }
        }
    }

    private static void summarize(List<?> targets) {
        Set<String> allowed = new HashSet<>(Set.of("Test", "Pantoprazol", "Loperamkda"));
        for (Object item : targets) {
            Map<?, ?> target = asMap(item);
            String label = asString(target.get("requested_label"));
            if (!allowed.remove(label)) throw new IllegalArgumentException();
            List<?> plans = asList(target.get("plans"));
            int records = 0, revisions = 0, multi = 0, correctionToPartial = 0;
            int explicitOccurrences = 0, missingOccurrences = 0, unresolvedCurrent = 0;
            int seriesMissing = 0, logicalScheduleMissing = 0, sharedOccurrenceGroups = 0;
            int scenarioRecords = 0, scenarioHistoricalCorrection = 0;
            int scenarioCurrentComplete = 0, scenarioCurrentPartial = 0, scenarioCurrentOther = 0;
            int describedCorrection = 0, describedCurrentComplete = 0;
            int historicalAnchors = 0, olderRevisionAnchors = 0, olderWithLogicalContinuation = 0;
            int olderWithoutLogicalContinuation = 0, unanchoredReferences = 0, ambiguousAnchors = 0;
            Set<String> scenarioPlans = new HashSet<>();
            List<Map<?, ?>> describedPantoprazolPlans = new ArrayList<>();
            Map<String, Set<String>> occurrencePlans = new HashMap<>();
            Set<String> series = new HashSet<>();
            Map<String, Set<String>> occurrenceRecords = new HashMap<>();
            for (Object planObject : plans) {
                Map<?, ?> plan = asMap(planObject);
                List<?> planRows = rows(plan, "plan");
                if (planRows.size() != 1) throw new IllegalArgumentException();
                Map<?, ?> planRow = asMap(planRows.get(0));
                String planId = asString(planRow.get("id"));
                Object rawSeries = planRow.get("medicationSeriesId");
                Map<String, Long> planRevisionNumbers = new HashMap<>();
                for (Object revisionObject : rows(plan, "plan_revisions")) {
                    Map<?, ?> revision = asMap(revisionObject);
                    Object idValue = revision.get("id");
                    Object numberValue = revision.get("revisionNumber");
                    if (idValue instanceof String && numberValue instanceof Number) {
                        planRevisionNumbers.put((String) idValue, ((Number) numberValue).longValue());
                    }
                }
                List<?> schedules = rows(plan, "administration_schedules");
                if ("Pantoprazol".equals(label) && matchesDescribedPantoprazolCreation(planRow)) {
                    describedPantoprazolPlans.add(planRow);
                }
                if (rawSeries instanceof String && !((String) rawSeries).isBlank()) series.add((String) rawSeries);
                else seriesMissing++;
                for (Object itemSchedule : rows(plan, "administration_schedules")) {
                    Object logical = asMap(itemSchedule).get("logicalId");
                    if (!(logical instanceof String) || ((String) logical).isBlank()) logicalScheduleMissing++;
                }
                Map<String, Map<?, ?>> recordRows = new LinkedHashMap<>();
                for (Object recordObject : rows(plan, "intake_records")) {
                    Map<?, ?> record = asMap(recordObject);
                    recordRows.put(asString(record.get("id")), record);
                }
                Map<String, List<Map<?, ?>>> byRecord = new HashMap<>();
                for (Object revisionObject : rows(plan, "intake_revisions")) {
                    Map<?, ?> rev = asMap(revisionObject);
                    byRecord.computeIfAbsent(asString(rev.get("intakeRecordId")), k -> new ArrayList<>()).add(rev);
                    revisions++;
                }
                records += recordRows.size();
                for (Map.Entry<String, Map<?, ?>> record : recordRows.entrySet()) {
                    List<Map<?, ?>> history = byRecord.getOrDefault(record.getKey(), Collections.emptyList());
                    if (history.size() > 1) multi++;
                    boolean complete = false, partial = false;
                    for (Map<?, ?> rev : history) {
                        complete |= "COMPLETED".equals(rev.get("outcome"));
                        partial |= "PARTIAL".equals(rev.get("outcome"));
                    }
                    Map<?, ?> current = null;
                    Object expectedRevision = record.getValue().get("currentRevisionNumber");
                    if (expectedRevision instanceof Number) {
                        long number = ((Number) expectedRevision).longValue();
                        for (Map<?, ?> rev : history) {
                            Object n = rev.get("revisionNumber");
                            if (n instanceof Number && ((Number) n).longValue() == number) {
                                if (current != null) throw new IllegalArgumentException();
                                current = rev;
                            }
                        }
                    }
                    if (current == null) {
                        unresolvedCurrent++;
                    } else {
                        if (complete && partial && "PARTIAL".equals(current.get("outcome"))) correctionToPartial++;
                        Object occurrence = current.get("scheduledOccurrenceId");
                        if (occurrence instanceof String && !((String) occurrence).isBlank()) {
                            explicitOccurrences++;
                            occurrenceRecords.computeIfAbsent(planId.length() + ":" + planId + occurrence, k -> new HashSet<>())
                                .add(record.getKey());
                            occurrencePlans.computeIfAbsent((String) occurrence, k -> new HashSet<>()).add(planId);
                            if ("Loperamkda".equals(label) && matchesLoperamkdaDay(current)) {
                                List<Map<?, ?>> anchors = matchingScheduleAnchors(planId, current, schedules);
                                if (anchors.isEmpty()) unanchoredReferences++;
                                else if (anchors.size() > 1) ambiguousAnchors++;
                                else {
                                    historicalAnchors++;
                                    Map<?, ?> anchor = anchors.get(0);
                                    Object revisionId = anchor.get("medicationPlanRevisionId");
                                    long anchorNumber = revisionId instanceof String
                                            ? planRevisionNumbers.getOrDefault((String) revisionId, Long.MIN_VALUE)
                                            : Long.MIN_VALUE;
                                    long currentNumber = number(planRow.get("currentRevisionNumber"));
                                    if (anchorNumber != Long.MIN_VALUE && currentNumber != Long.MIN_VALUE
                                            && anchorNumber < currentNumber) {
                                        olderRevisionAnchors++;
                                        Object logicalId = anchor.get("logicalId");
                                        boolean continuity = logicalId instanceof String && !((String) logicalId).isBlank()
                                                && schedules.stream().map(ReadIdentityDiagnostics::asMap).anyMatch(schedule ->
                                                    Objects.equals(logicalId, schedule.get("logicalId"))
                                                    && planRevisionNumbers.getOrDefault(
                                                        String.valueOf(schedule.get("medicationPlanRevisionId")),
                                                        Long.MIN_VALUE) == currentNumber);
                                        if (continuity) olderWithLogicalContinuation++;
                                        else olderWithoutLogicalContinuation++;
                                    }
                                }
                            }
                        } else missingOccurrences++;
                    }
                    if ("Test".equals(label) && history.stream().anyMatch(ReadIdentityDiagnostics::matchesTestScenario)) {
                        scenarioRecords++;
                        scenarioPlans.add(planId);
                        if (complete && partial) scenarioHistoricalCorrection++;
                        if (current != null && "COMPLETED".equals(current.get("outcome"))) scenarioCurrentComplete++;
                        else if (current != null && "PARTIAL".equals(current.get("outcome"))) scenarioCurrentPartial++;
                        else scenarioCurrentOther++;
                        boolean hadFullAmount = history.stream().anyMatch(rev -> matchesTestScenario(rev)
                                && "COMPLETED".equals(rev.get("outcome")) && decimalEquals(rev.get("actualAmountValue"), "3"));
                        boolean hadPartialAmount = history.stream().anyMatch(rev -> matchesTestScenario(rev)
                                && "PARTIAL".equals(rev.get("outcome")) && decimalEquals(rev.get("actualAmountValue"), "2"));
                        if (hadFullAmount && hadPartialAmount && current != null
                                && "PARTIAL".equals(current.get("outcome"))
                                && decimalEquals(current.get("actualAmountValue"), "2")) describedCorrection++;
                        if (current != null && matchesTestScenario(current)
                                && "COMPLETED".equals(current.get("outcome"))
                                && decimalEquals(current.get("actualAmountValue"), "3")) describedCurrentComplete++;
                    }
                    if ("Loperamkda".equals(label) && history.stream().anyMatch(ReadIdentityDiagnostics::matchesLoperamkdaDay)) {
                        scenarioRecords++;
                    }
                }
                for (String section : List.of("plan", "plan_revisions", "administration_schedules",
                        "intake_records", "intake_revisions")) {
                    Map<?, ?> result = asMap(plan.get(section));
                    if (!Boolean.TRUE.equals(result.get("filter_available")) ||
                            !asList(result.get("unavailable_columns")).isEmpty()) {
                        System.out.println(label + ": existen campos históricos o relaciones no disponibles.");
                        break;
                    }
                }
            }
            for (Set<String> ids : occurrenceRecords.values()) if (ids.size() > 1) sharedOccurrenceGroups++;
            System.out.println(label + ": planes=" + plans.size() + ", series identificadas=" + series.size()
                    + ", planes sin serie=" + seriesMissing + ", registros=" + records
                    + ", revisiones=" + revisions + ", registros con varias revisiones=" + multi + ".");
            System.out.println(label + ": resultados efectivos no resolubles=" + unresolvedCurrent
                    + ", referencias de ocasión almacenadas=" + explicitOccurrences
                    + ", registros sin vínculo explícito=" + missingOccurrences
                    + ", ocasiones compartidas por varios registros=" + sharedOccurrenceGroups
                    + ", horarios sin identidad lógica=" + logicalScheduleMissing + ".");
            if ("Test".equals(label)) {
                System.out.println("Test: registros candidatos del caso descrito=" + scenarioRecords
                        + ", candidatos con ambos estados en su historial=" + scenarioHistoricalCorrection
                        + ", registros con resultado parcial actual tras ambos estados=" + correctionToPartial + ".");
                System.out.println("Test: planes de los candidatos=" + scenarioPlans.size()
                        + ", estados actuales completos=" + scenarioCurrentComplete
                        + ", parciales=" + scenarioCurrentPartial + ", otros o desconocidos=" + scenarioCurrentOther
                        + ", identidades con la correccion descrita=" + describedCorrection
                        + ", identidades con el completo descrito vigente=" + describedCurrentComplete + ".");
            }
            if ("Pantoprazol".equals(label)) {
                long acrossPlans = occurrencePlans.values().stream().filter(ids -> ids.size() > 1).count();
                System.out.println("Pantoprazol: referencias exactamente compartidas entre planes=" + acrossPlans
                        + "; relacion entre las altas descritas=" + describePlanRelationship(describedPantoprazolPlans) + ".");
            }
            if ("Loperamkda".equals(label)) {
                System.out.println("Loperamkda: registros candidatos del día descrito=" + scenarioRecords + ".");
                System.out.println("Loperamkda: referencias ancladas sintacticamente a un horario historico="
                        + historicalAnchors + ", de revision anterior=" + olderRevisionAnchors
                        + ", con continuidad logica registrada=" + olderWithLogicalContinuation
                        + ", sin continuidad logica demostrable=" + olderWithoutLogicalContinuation
                        + ", sin anclaje=" + unanchoredReferences + ", con varios anclajes=" + ambiguousAnchors + ".");
            }
        }
        if (!allowed.isEmpty()) throw new IllegalArgumentException();
        System.out.println("Las coincidencias no prueban identidad; faltan contrastar las ocasiones proyectadas y el reporte.");
    }

    private static List<Map<?, ?>> matchingScheduleAnchors(String planId, Map<?, ?> record, List<?> schedules) {
        Object rawReference = record.get("scheduledOccurrenceId");
        long day = number(record.get("scheduledEpochDay"));
        if (!(rawReference instanceof String) || day == Long.MIN_VALUE) return Collections.emptyList();
        List<Map<?, ?>> matches = new ArrayList<>();
        for (Object object : schedules) {
            Map<?, ?> schedule = asMap(object);
            Object revisionId = schedule.get("medicationPlanRevisionId");
            Object scheduleId = schedule.get("id");
            if (!(revisionId instanceof String) || !(scheduleId instanceof String)) continue;
            for (int offset = -1; offset <= 1; offset++) {
                String candidate = planId + ":" + revisionId + ":" + scheduleId + ":"
                        + LocalDate.ofEpochDay(day).plusDays(offset);
                if (candidate.equals(rawReference)) { matches.add(schedule); break; }
            }
        }
        return matches;
    }
    private static boolean decimalEquals(Object raw, String expected) {
        if (!(raw instanceof String)) return false;
        try { return new java.math.BigDecimal((String) raw).compareTo(new java.math.BigDecimal(expected)) == 0; }
        catch (NumberFormatException ignored) { return false; }
    }
    private static boolean matchesDescribedPantoprazolCreation(Map<?, ?> plan) {
        Object timestamp = plan.get("createdAtEpochMillis");
        if (!(timestamp instanceof Number)) return false;
        java.time.ZonedDateTime local = java.time.Instant.ofEpochMilli(((Number) timestamp).longValue())
                .atZone(java.time.ZoneId.of("America/Mexico_City"));
        return local.toLocalDate().equals(LocalDate.of(2026, 11, 16))
                && ((local.getHour() == 16 && local.getMinute() == 43)
                    || (local.getHour() == 17 && local.getMinute() == 3));
    }
    private static String describePlanRelationship(List<Map<?, ?>> candidates) {
        if (candidates.size() != 2) return "no determinable con las altas disponibles y la zona local";
        Object firstId = candidates.get(0).get("id"), secondId = candidates.get(1).get("id");
        if (Objects.equals(firstId, secondId)) return "misma identidad de plan";
        Object a = candidates.get(0).get("medicationSeriesId"), b = candidates.get(1).get("medicationSeriesId");
        if (!(a instanceof String) || !(b instanceof String) || ((String) a).isBlank() || ((String) b).isBlank()) {
            return "planes distintos sin serie comparable";
        }
        return Objects.equals(a, b) ? "planes distintos de una misma serie" : "planes de series distintas";
    }
    private static boolean matchesTestScenario(Map<?, ?> revision) {
        return number(revision.get("scheduledEpochDay")) == LocalDate.of(2026, 11, 12).toEpochDay()
                && number(revision.get("scheduledMinute")) == 21 * 60 + 30
                && number(revision.get("actualMinute")) == 19 * 60;
    }
    private static boolean matchesLoperamkdaDay(Map<?, ?> revision) {
        return number(revision.get("scheduledEpochDay")) == LocalDate.of(2026, 11, 14).toEpochDay();
    }
    private static long number(Object value) {
        return value instanceof Number ? ((Number) value).longValue() : Long.MIN_VALUE;
    }
    private static List<?> rows(Map<?, ?> plan, String section) {
        return asList(asMap(plan.get(section)).get("rows"));
    }
    private static Map<?, ?> asMap(Object value) {
        if (!(value instanceof Map)) throw new IllegalArgumentException();
        return (Map<?, ?>) value;
    }
    private static List<?> asList(Object value) {
        if (!(value instanceof List)) throw new IllegalArgumentException();
        return (List<?>) value;
    }
    private static String asString(Object value) {
        if (!(value instanceof String)) throw new IllegalArgumentException();
        return (String) value;
    }
    private static String digest(Path file) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(file)) {
            byte[] buffer = new byte[32768];
            int count;
            while ((count = in.read(buffer)) != -1) md.update(buffer, 0, count);
            Arrays.fill(buffer, (byte) 0);
        }
        StringBuilder out = new StringBuilder();
        for (byte value : md.digest()) out.append(String.format("%02x", value & 255));
        return out.toString();
    }

    /** Small strict parser for the JSON generated by Android's JsonWriter. */
    private static final class JsonParser {
        private final String input;
        private int position;
        JsonParser(String input) { this.input = input; }
        Object parse() {
            Object value = value();
            space();
            if (position != input.length()) throw new IllegalArgumentException();
            return value;
        }
        private Object value() {
            space();
            if (position >= input.length()) throw new IllegalArgumentException();
            char c = input.charAt(position);
            if (c == '{') return object();
            if (c == '[') return array();
            if (c == '"') return string();
            if (c == 't' && literal("true")) return Boolean.TRUE;
            if (c == 'f' && literal("false")) return Boolean.FALSE;
            if (c == 'n' && literal("null")) return null;
            if (c == '-' || (c >= '0' && c <= '9')) return numberValue();
            throw new IllegalArgumentException();
        }
        private Map<String, Object> object() {
            Map<String, Object> result = new LinkedHashMap<>();
            position++;
            space();
            if (take('}')) return result;
            do {
                space();
                String key = string();
                space();
                if (!take(':') || result.containsKey(key)) throw new IllegalArgumentException();
                result.put(key, value());
                space();
                if (take('}')) return result;
            } while (take(','));
            throw new IllegalArgumentException();
        }
        private List<Object> array() {
            List<Object> result = new ArrayList<>();
            position++;
            space();
            if (take(']')) return result;
            do {
                result.add(value());
                space();
                if (take(']')) return result;
            } while (take(','));
            throw new IllegalArgumentException();
        }
        private String string() {
            if (!take((char) 34)) throw new IllegalArgumentException();
            StringBuilder out = new StringBuilder();
            while (position < input.length()) {
                char c = input.charAt(position++);
                if (c == 34) return out.toString();
                if (c < 32) throw new IllegalArgumentException();
                if (c != 92) { out.append(c); continue; }
                if (position == input.length()) throw new IllegalArgumentException();
                char escaped = input.charAt(position++);
                switch (escaped) {
                    case 34: case 92: case 47: out.append(escaped); break;
                    case 98: out.append((char) 8); break;
                    case 102: out.append((char) 12); break;
                    case 110: out.append((char) 10); break;
                    case 114: out.append((char) 13); break;
                    case 116: out.append((char) 9); break;
                    case 117:
                        if (position + 4 > input.length()) throw new IllegalArgumentException();
                        out.append((char) Integer.parseInt(input.substring(position, position + 4), 16));
                        position += 4;
                        break;
                    default: throw new IllegalArgumentException();
                }
            }
            throw new IllegalArgumentException();
        }
        private Number numberValue() {
            int start = position;
            while (position < input.length() && "-+0123456789.eE".indexOf(input.charAt(position)) >= 0) position++;
            String value = input.substring(start, position);
            if (value.indexOf('.') >= 0 || value.indexOf('e') >= 0 || value.indexOf('E') >= 0) {
                return Double.valueOf(value);
            }
            return Long.valueOf(value);
        }
        private boolean literal(String value) {
            if (!input.startsWith(value, position)) return false;
            position += value.length();
            return true;
        }
        private boolean take(char c) {
            if (position < input.length() && input.charAt(position) == c) { position++; return true; }
            return false;
        }
        private void space() {
            while (position < input.length()) {
                char c = input.charAt(position);
                if (c != 32 && c != 9 && c != 10 && c != 13) return;
                position++;
            }
        }
    }
}
