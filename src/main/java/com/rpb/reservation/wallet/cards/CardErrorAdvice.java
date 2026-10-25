package com.rpb.reservation.wallet.cards;
import com.rpb.reservation.cards.application.CardException;
import com.rpb.reservation.catalog.domain.CatalogException;
import com.rpb.reservation.wallet.domain.WalletException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(basePackages="com.rpb.reservation.wallet.cards")
public class CardErrorAdvice {
 @ExceptionHandler(CardException.class)public ResponseEntity<?> card(CardException e){return reply(e.status(),e.code(),e.getMessage());}
 @ExceptionHandler(CatalogException.class)public ResponseEntity<?> catalog(CatalogException e){return reply(e.status(),e.code(),e.getMessage());}
 @ExceptionHandler(WalletException.class)public ResponseEntity<?> wallet(WalletException e){return reply(e.status(),e.code(),e.getMessage());}
 @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})public ResponseEntity<?> invalid(){return card(CardException.invalid());}
 @ExceptionHandler(org.springframework.dao.DataAccessException.class)public ResponseEntity<?> conflict(){return reply(409,"CARD_CONFLICT","状态冲突，请先查询原操作结果，勿重复收款");}
 private ResponseEntity<?> reply(int status,String code,String message){return ResponseEntity.status(status).body(Map.of("code",code,"message",message));}
}
