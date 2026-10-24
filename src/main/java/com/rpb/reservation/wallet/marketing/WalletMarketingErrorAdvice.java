package com.rpb.reservation.wallet.marketing;
import com.rpb.reservation.wallet.domain.WalletException;
import java.util.*;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes={WalletMarketingController.class,WalletCustomerTopupController.class})
public class WalletMarketingErrorAdvice {
 @ExceptionHandler(WalletException.class) public ResponseEntity<Map<String,Object>> wallet(WalletException e){Map<String,Object> body=new LinkedHashMap<>();body.put("code",e.code());body.put("message",e.getMessage());if(e instanceof WalletCustomerTopupService.PendingTopupException pending)body.put("pendingTopupId",pending.pendingTopupId());var response=ResponseEntity.status(e.status());if(e.status()==429)response.header("Retry-After","60");return response.body(body);}
 @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class}) public ResponseEntity<Map<String,Object>> invalid(Exception e){return wallet(WalletException.invalid());}
 @ExceptionHandler(DataAccessException.class) public ResponseEntity<Map<String,Object>> conflict(DataAccessException e){return wallet(WalletException.conflict("交易状态冲突，请查询原交易后重试"));}
}
