package com.rpb.reservation.workforce;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class WorkforceMigrationCatalogTest {
 private static final Path ROOT=Path.of("src/main/resources/db/migration");
 @Test void releasedV120ResourcesRemainUnchanged() throws Exception {
  var baseline=new ObjectMapper().readTree(Path.of("src/test/resources/workforce/released-v120-migrations.json").toFile());
  assertThat(baseline.path("migrations")).hasSize(120);
  for(var migration:baseline.path("migrations")){
   Path file=ROOT.resolve(migration.path("script").asText());
   assertThat(file).as("released migration %s",migration.path("version").asText()).exists();
   String normalized=Files.readString(file).replace("\r\n","\n");
   String digest=HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(normalized.getBytes(StandardCharsets.UTF_8)));
   assertThat(digest).as(file.getFileName().toString()).isEqualTo(migration.path("normalizedSha256").asText());
  }
 }
 @Test void newAttendanceMigrationsAppendAfterTheReleasedSequence() throws Exception {
  var expected=List.of("V121__workforce_attendance_policies.sql","V122__workforce_attendance_daily.sql","V123__workforce_leave_entitlements.sql","V124__workforce_attendance_system_evaluation.sql","V125__workforce_interval_leave_evidence.sql");
  for(String name:expected)assertThat(ROOT.resolve(name)).exists();
  try(var files=Files.list(ROOT)){
   var names=files.map(p->p.getFileName().toString()).filter(n->n.matches("V[0-9]+__.*\\.sql")).toList();
   var versions=names.stream().map(n->Integer.parseInt(n.substring(1,n.indexOf("__")))).sorted().toList();
   assertThat(versions).doesNotHaveDuplicates();
   assertThat(versions).containsAll(java.util.stream.IntStream.rangeClosed(1,125).boxed().toList());
  }
 }
}
