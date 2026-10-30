package com.rpb.reservation.workforce.api;
import com.rpb.reservation.walkin.api.CurrentActorProvider;
import com.rpb.reservation.workforce.positions.PositionService;
import static com.rpb.reservation.workforce.positions.PositionDtos.*;
import java.util.*;
import org.springframework.web.bind.annotation.*;
@RestController
@RequestMapping("/api/v1/stores/{storeId}/workforce/positions")
public class WorkforcePositionController extends WorkforceEndpointSupport {
 private final PositionService positions;
 public WorkforcePositionController(CurrentActorProvider actors,PositionService positions){super(actors);this.positions=positions;}
 @GetMapping public List<Position> list(@PathVariable UUID storeId){return positions.list(actor(),storeId);}
 @PutMapping("/{id}") public Position save(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody PositionWrite c){return positions.save(actor(),storeId,id,c);}
 @PutMapping("/{id}/deletion") public Deleted delete(@PathVariable UUID storeId,@PathVariable UUID id,@RequestBody DeleteWrite c){return positions.delete(actor(),storeId,id,c);}
}
