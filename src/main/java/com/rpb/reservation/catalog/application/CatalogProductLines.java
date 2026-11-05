package com.rpb.reservation.catalog.application;
import com.rpb.reservation.catalog.domain.CatalogException;
import java.util.*;
/** Category applicability uses registered product owners; channels remain product-level settings. */
public final class CatalogProductLines {
 private CatalogProductLines(){}
 public static final Set<String> SUPPORTED=Set.of("wallet","wine_storage");
 public static String owner(String channel){return switch(channel){case "storage"->"wine_storage";case "cards","rewards","shopping"->"wallet";default->throw CatalogException.invalid();};}
 public static boolean covers(List<String> lines,Collection<String> channels){return lines!=null&&channels.stream().map(CatalogProductLines::owner).allMatch(lines::contains);}
}
