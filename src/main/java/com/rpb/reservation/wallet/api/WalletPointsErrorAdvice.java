package com.rpb.reservation.wallet.api;
import com.rpb.reservation.wallet.domain.WalletException;
import com.rpb.reservation.loyalty.domain.PointsException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(assignableTypes={com.rpb.reservation.wallet.merchantorder.MerchantOrderController.class,com.rpb.reservation.wallet.redemption.RedemptionController.class,WalletPointsStaffController.class,WalletPointsCustomerController.class})
public class WalletPointsErrorAdvice {
 @ExceptionHandler(PointsException.class)public ResponseEntity<Map<String,String>> points(PointsException e){return ResponseEntity.status(e.status()).body(Map.of("code",e.code(),"message",e.getMessage()));}
 @ExceptionHandler(WalletException.class)public ResponseEntity<Map<String,String>> wallet(WalletException e){return ResponseEntity.status(e.status()).body(Map.of("code",e.code(),"message",e.getMessage()));}
 @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})public ResponseEntity<Map<String,String>> invalid(Exception e){Throwable t=e;while(t.getCause()!=null)t=t.getCause();if(t instanceof PointsException p)return points(p);return points(PointsException.invalid());}
 @ExceptionHandler(DataAccessException.class)public ResponseEntity<Map<String,String>> conflict(DataAccessException e){return points(PointsException.conflict("POINTS_CONFLICT","积分状态冲突，请查询原记录后重试"));}
}
