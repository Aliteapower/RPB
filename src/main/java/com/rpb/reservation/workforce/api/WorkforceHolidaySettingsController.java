package com.rpb.reservation.workforce.api;
import com.rpb.reservation.workforce.calendar.annual.*;import static com.rpb.reservation.workforce.calendar.annual.HolidayDtos.*;import com.rpb.reservation.walkin.api.CurrentActorProvider;import java.util.UUID;import org.springframework.web.bind.annotation.*;
@RestController @RequestMapping("/api/v1/stores/{storeId}/workforce")
public class WorkforceHolidaySettingsController extends WorkforceEndpointSupport {
 private final PublicHolidayService holidays;private final StoreRestMonthService rest;
 public WorkforceHolidaySettingsController(CurrentActorProvider actors,PublicHolidayService holidays,StoreRestMonthService rest){super(actors);this.holidays=holidays;this.rest=rest;}
 @GetMapping("/public-holidays/{year}") public YearPage year(@PathVariable UUID storeId,@PathVariable int year){return holidays.get(actor(),storeId,year);}
 @PutMapping("/public-holidays/{year}") public YearPage saveYear(@PathVariable UUID storeId,@PathVariable int year,@RequestBody Write c){return holidays.save(actor(),storeId,year,c);}
 @GetMapping("/public-holiday-templates/{year}") public Template template(@PathVariable UUID storeId,@PathVariable int year){return holidays.template(actor(),storeId,year);}
 @GetMapping("/store-rest-months/{month}") public MonthPage month(@PathVariable UUID storeId,@PathVariable String month){return rest.get(actor(),storeId,month);}
 @PutMapping("/store-rest-months/{month}") public MonthPage saveMonth(@PathVariable UUID storeId,@PathVariable String month,@RequestBody MonthWrite c){return rest.save(actor(),storeId,month,c);}
}
