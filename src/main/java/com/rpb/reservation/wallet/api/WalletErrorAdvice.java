package com.rpb.reservation.wallet.api;

import com.rpb.reservation.wallet.domain.WalletException;
import java.util.Map;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestControllerAdvice(assignableTypes={WalletStaffController.class,WalletCustomerController.class,WalletSupportController.class,WalletCollectionSettingsController.class,WalletScanChargeController.class,WalletFundingController.class,WalletCustomerIdentityController.class})
public class WalletErrorAdvice {
    @ExceptionHandler(WalletException.class)
    public ResponseEntity<Map<String,String>> wallet(WalletException e) {return ResponseEntity.status(e.status()).body(Map.of("code",e.code(),"message",e.getMessage()));}
    @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})
    public ResponseEntity<Map<String,String>> invalid(Exception e) {return wallet(WalletException.invalid());}
    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String,String>> conflict(DataAccessException e) {return wallet(WalletException.conflict("交易状态冲突，请查询原交易结果后重试，勿重复收款"));}
}
