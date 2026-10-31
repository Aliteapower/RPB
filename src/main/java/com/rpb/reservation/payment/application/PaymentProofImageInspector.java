package com.rpb.reservation.payment.application;

import com.rpb.reservation.common.scope.StoreScope;
import java.nio.file.*;
import java.io.IOException;
import org.springframework.stereotype.Service;

/** Shared OCR/template capability. Never queries an intent or confirms any payment. */
@Service
public class PaymentProofImageInspector {
    private final PaymentProofOcrAdapter ocr;
    private final PaymentProofTemplateService templates;
    public PaymentProofImageInspector(PaymentProofOcrAdapter ocr,PaymentProofTemplateService templates){this.ocr=ocr;this.templates=templates;}
    public PaymentProofOcrFields inspect(StoreScope scope,byte[] bytes,String filename){
        Path file=null;
        try {
            String safe=filename==null?"":filename.trim().toLowerCase();String suffix=safe.contains(".")?safe.substring(safe.lastIndexOf('.')):".img";if(!suffix.matches("\\.[a-z0-9]{2,5}"))suffix=".img";
            file=Files.createTempFile("rpb-payment-proof-",suffix);Files.write(file,bytes);
            var fields=ocr.extract(file,new PaymentProofOcrExpected(null,null));
            return templates==null?fields:templates.enhance(scope,fields);
        }catch(IOException e){throw new PaymentServiceException(PaymentServiceErrorCode.REQUEST_INVALID);}
        finally{if(file!=null)try{Files.deleteIfExists(file);}catch(IOException ignored){}}
    }
}
