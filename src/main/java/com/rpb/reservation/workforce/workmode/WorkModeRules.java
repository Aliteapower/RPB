package com.rpb.reservation.workforce.workmode;
import static com.rpb.reservation.workforce.workmode.WorkModeDtos.*;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.time.*;import java.util.*;
/** Pure input validation and spherical-distance calculation; no permissions or persistence. */
public final class WorkModeRules {
 private WorkModeRules(){}
 public static String mode(String mode){if(mode==null||!Set.of("legacy","onsite","field","home","free").contains(mode))throw WorkforceException.invalid();return mode;}
 public static Point point(Point p){if(p==null||p.label()==null||p.label().isBlank()||p.label().length()>200||!coordinates(p.latitude(),p.longitude())||!finite(p.radiusMeters())||p.radiusMeters()<10||p.radiusMeters()>1000)throw WorkforceException.invalid();return new Point(p.label().trim(),p.latitude(),p.longitude(),p.radiusMeters());}
 private static boolean finite(Double value){return value!=null&&Double.isFinite(value);}
 private static boolean coordinates(Double lat,Double lon){return finite(lat)&&finite(lon)&&Math.abs(lat)<=90&&Math.abs(lon)<=180;}
 public static void location(Location l){if(l==null)return;if(l.description()!=null&&l.description().length()>200)throw WorkforceException.invalid();boolean hasCoordinates=l.latitude()!=null||l.longitude()!=null||l.accuracyMeters()!=null||l.capturedAt()!=null;if(hasCoordinates&&(!coordinates(l.latitude(),l.longitude())||!finite(l.accuracyMeters())||l.accuracyMeters()<0||l.capturedAt()==null))throw WorkforceException.invalid();}
 public static void locate(Point p,Location l,Instant now){
  if(l==null||l.latitude()==null||l.longitude()==null||l.accuracyMeters()==null||l.capturedAt()==null)throw new WorkforceException("WORKFORCE_LOCATION_REQUIRED",400);
  point(p);location(l);if(l.capturedAt().isBefore(now.minusSeconds(120))||l.capturedAt().isAfter(now.plusSeconds(30)))throw new WorkforceException("WORKFORCE_LOCATION_STALE",409);
  if(l.accuracyMeters()>100||l.accuracyMeters()>p.radiusMeters())throw new WorkforceException("WORKFORCE_LOCATION_ACCURACY",409);
  if(distance(p.latitude(),p.longitude(),l.latitude(),l.longitude())>p.radiusMeters())throw new WorkforceException("WORKFORCE_LOCATION_OUTSIDE",409);
 }
 public static double distance(double lat1,double lon1,double lat2,double lon2){double a=Math.pow(Math.sin(Math.toRadians(lat2-lat1)/2),2)+Math.cos(Math.toRadians(lat1))*Math.cos(Math.toRadians(lat2))*Math.pow(Math.sin(Math.toRadians(lon2-lon1)/2),2);return 6371000*2*Math.asin(Math.sqrt(Math.min(1,Math.max(0,a))));}
}
