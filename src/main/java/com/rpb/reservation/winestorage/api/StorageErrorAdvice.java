package com.rpb.reservation.winestorage.api;

import com.rpb.reservation.winestorage.domain.StorageException;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.*;

@RestControllerAdvice(basePackages="com.rpb.reservation.winestorage.api")
public class StorageErrorAdvice {
 @ExceptionHandler(StorageException.class)public ResponseEntity<?> error(StorageException e){return ResponseEntity.status(e.status()).body(Map.of("code",e.code(),"message",e.getMessage(),"problems",e.problems()));}
 @ExceptionHandler(com.rpb.reservation.catalog.domain.CatalogException.class)public ResponseEntity<?> catalog(com.rpb.reservation.catalog.domain.CatalogException e){return ResponseEntity.status(e.status()).body(Map.of("code",e.code(),"message",e.getMessage()));}
 @ExceptionHandler({IllegalArgumentException.class,org.springframework.http.converter.HttpMessageNotReadableException.class})public ResponseEntity<?> invalid(){return error(StorageException.invalid());}
 @ExceptionHandler(org.springframework.dao.DataIntegrityViolationException.class)public ResponseEntity<?> conflict(){return error(StorageException.conflict("资料已变化或引用不匹配，请刷新后核对"));}
}
