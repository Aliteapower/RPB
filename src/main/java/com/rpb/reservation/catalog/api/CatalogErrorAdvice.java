package com.rpb.reservation.catalog.api;
import com.rpb.reservation.catalog.domain.CatalogException;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
@RestControllerAdvice(basePackages="com.rpb.reservation.catalog")
public class CatalogErrorAdvice {
 @ExceptionHandler(CatalogException.class)public ResponseEntity<?> error(CatalogException e){return ResponseEntity.status(e.status()).body(Map.of("code",e.code(),"message",e.getMessage()));}
 @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})public ResponseEntity<?> invalid(){return error(CatalogException.invalid());}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)public ResponseEntity<?> conflict(){return ResponseEntity.status(409).body(Map.of("code","CATALOG_CONFLICT","message","产品编号重复或资料已被引用"));}
}
