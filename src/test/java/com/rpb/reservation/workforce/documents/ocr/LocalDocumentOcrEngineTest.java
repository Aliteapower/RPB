package com.rpb.reservation.workforce.documents.ocr;

import static org.assertj.core.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;
import com.rpb.reservation.workforce.documents.DocumentDtos.FileData;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.awt.Color;
import java.awt.Font;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.*;
import javax.imageio.ImageIO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class LocalDocumentOcrEngineTest {
    @TempDir Path directory;
    List<String> command(String mode){return List.of(Path.of(System.getProperty("java.home"),"bin","java.exe").toString(),"-Xmx32m","-XX:MaxMetaspaceSize=64m","-cp",System.getProperty("java.class.path"),FakeProcess.class.getName(),mode);}
    LocalDocumentOcrEngine engine(String mode,Duration duration){return new LocalDocumentOcrEngine(command(mode),command("pdf"),directory,duration,1,64*1024);}
    FileData file(String type){return new FileData(UUID.randomUUID(),"secret",type,new byte[]{1,2},"hash",false);}
    @Test void processOutputIsBoundedAndTemporaryFilesAreCleaned()throws Exception {
        assertThatThrownBy(()->engine("overflow",Duration.ofSeconds(5)).recognize(file("image/png"))).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_DOCUMENT_OCR_UNAVAILABLE");
        try(var paths=Files.list(directory)){assertThat(paths.count()).isZero();}
    }
    @Test void deadlineKillsProcessAndCleansTemporaryDirectory()throws Exception {
        long start=System.nanoTime();
        assertThatThrownBy(()->engine("sleep",Duration.ofMillis(300)).recognize(file("image/png"))).isInstanceOf(WorkforceException.class);
        assertThat(Duration.ofNanos(System.nanoTime()-start)).isLessThan(Duration.ofSeconds(5));
        try(var paths=Files.list(directory)){assertThat(paths.count()).isZero();}
    }
    @Test void missingCommandReturnsUnavailable() {
        var engine=new LocalDocumentOcrEngine(List.of(directory.resolve("missing.exe").toString()),List.of("missing-pdftoppm"),directory,Duration.ofSeconds(2),1,65536);
        assertThatThrownBy(()->engine.recognize(file("application/pdf"))).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_DOCUMENT_OCR_UNAVAILABLE");
    }
    @Test void pdfOnlyRendersAndRecognizesTwoPages() {
        assertThat(engine("text",Duration.ofSeconds(10)).recognize(file("application/pdf"))).contains("Name: TAN");
    }
    @Test void pdfRendererFailureDoesNotReturnPartialRecognition() {
        var engine=new LocalDocumentOcrEngine(command("text"),command("failure"),directory,Duration.ofSeconds(5),1,65536);
        assertThatThrownBy(()->engine.recognize(file("application/pdf"))).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_DOCUMENT_OCR_UNAVAILABLE");
    }
    @Test void concurrencyLimitRejectsInsteadOfQueuing()throws Exception {
        var engine=engine("sleep",Duration.ofSeconds(2));
        var first=new java.util.concurrent.FutureTask<String>(() -> engine.recognize(file("image/png")));
        Thread thread=Thread.ofVirtual().start(first);
        long until=System.nanoTime()+Duration.ofSeconds(1).toNanos();
        while(System.nanoTime()<until){try(var paths=Files.list(directory)){if(paths.findAny().isPresent())break;}Thread.sleep(10);}
        assertThatThrownBy(()->engine.recognize(file("image/png"))).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_DOCUMENT_OCR_BUSY");
        assertThatThrownBy(()->first.get(5,java.util.concurrent.TimeUnit.SECONDS)).isInstanceOf(java.util.concurrent.ExecutionException.class);
        thread.join(1000);
    }
    @Test void rejectsUnsupportedFormatBeforeStartingProcess() {
        assertThatThrownBy(()->engine("text",Duration.ofSeconds(3)).recognize(file("application/zip"))).isInstanceOf(WorkforceException.class).extracting("code").isEqualTo("WORKFORCE_DOCUMENT_OCR_UNSUPPORTED");
    }
    @Test void realSyntheticImageUsesInstalledTesseract()throws Exception {
        Path executable=Path.of("C:/Program Files/Tesseract-OCR/tesseract.exe");assumeTrue(Files.isExecutable(executable));
        BufferedImage image=new BufferedImage(1600,500,BufferedImage.TYPE_INT_RGB);Graphics2D graphics=image.createGraphics();
        graphics.setColor(Color.WHITE);graphics.fillRect(0,0,1600,500);graphics.setColor(Color.BLACK);graphics.setFont(new Font("Arial",Font.PLAIN,52));
        graphics.drawString("Name: TAN AH KOW",40,90);graphics.drawString("NRIC No: T1234567A",40,190);graphics.drawString("Date of Approval: 15 Oct 2024",40,290);graphics.drawString("Valid Until: 2029-10-15",40,390);graphics.dispose();
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();ImageIO.write(image,"png",bytes);
        var engine=new LocalDocumentOcrEngine(List.of(executable.toString()),List.of("pdftoppm"),directory,Duration.ofSeconds(30),1,65536);
        String text=engine.recognize(new FileData(UUID.randomUUID(),"synthetic.png","image/png",bytes.toByteArray(),"hash",false));
        var fields=new DocumentOcrParser().parse(text);
        assertThat(fields.name()).isEqualTo("TAN AH KOW");assertThat(fields.number()).as("synthetic OCR: %s",text).isEqualTo("T1234567A");
        assertThat(fields.approvedOn()).isEqualTo(java.time.LocalDate.of(2024,10,15));assertThat(fields.expiresOn()).isEqualTo(java.time.LocalDate.of(2029,10,15));
    }
    @Test void realThreePageSyntheticPdfOnlyReadsFirstTwoPages() {
        String renderer=System.getenv("WORKFORCE_OCR_PDFTOPPM_CMD");
        Path executable=Path.of("C:/Program Files/Tesseract-OCR/tesseract.exe");assumeTrue(renderer!=null&&Files.isExecutable(Path.of(renderer))&&Files.isExecutable(executable));
        var engine=new LocalDocumentOcrEngine(List.of(executable.toString()),List.of(renderer),directory,Duration.ofSeconds(30),1,65536);
        String text=engine.recognize(new FileData(UUID.randomUUID(),"synthetic.pdf","application/pdf",syntheticPdf(),"hash",false));
        var fields=new DocumentOcrParser().parse(text);
        assertThat(fields.name()).isEqualTo("TAN AH KOW");assertThat(fields.number()).as("synthetic OCR: %s",text).isEqualTo("T1234567A");
        assertThat(text).doesNotContain("LIM AH KOW");
    }
    private byte[] syntheticPdf() {
        var objects=new ArrayList<String>();
        objects.add("<< /Type /Catalog /Pages 2 0 R >>");objects.add("<< /Type /Pages /Kids [4 0 R 6 0 R 8 0 R] /Count 3 >>");objects.add("<< /Type /Font /Subtype /Type1 /BaseFont /Helvetica >>");
        for(int page=0;page<3;page++) {
            objects.add("<< /Type /Page /Parent 2 0 R /MediaBox [0 0 612 792] /Resources << /Font << /F1 3 0 R >> >> /Contents "+(5+page*2)+" 0 R >>");
            String content="BT /F1 26 Tf 50 700 Td ("+(page==0?"Name: TAN AH KOW":page==1?"NRIC No: T1234567A":"Name: LIM AH KOW")+") Tj ET";
            objects.add("<< /Length "+content.length()+" >>\nstream\n"+content+"\nendstream");
        }
        StringBuilder pdf=new StringBuilder("%PDF-1.4\n");List<Integer> offsets=new ArrayList<>();
        for(int i=0;i<objects.size();i++){offsets.add(pdf.length());pdf.append(i+1).append(" 0 obj\n").append(objects.get(i)).append("\nendobj\n");}
        int xref=pdf.length();pdf.append("xref\n0 10\n0000000000 65535 f \n");
        for(int offset:offsets)pdf.append(String.format(Locale.ROOT,"%010d 00000 n \n",offset));
        pdf.append("trailer\n<< /Size 10 /Root 1 0 R >>\nstartxref\n").append(xref).append("\n%%EOF\n");
        return pdf.toString().getBytes(java.nio.charset.StandardCharsets.US_ASCII);
    }
    public static class FakeProcess {
        public static void main(String[] args)throws Exception {
            switch(args[0]){
                case "overflow"->System.out.print("x".repeat(70000));
                case "sleep"->Thread.sleep(20000);
                case "failure"->System.exit(1);
                case "pdf"->{String prefix=args[args.length-1];for(int i=1;i<=2;i++)ImageIO.write(new BufferedImage(20,20,BufferedImage.TYPE_INT_RGB),"png",new File(prefix+"-"+i+".png"));}
                default->System.out.println("Name: TAN");
            }
        }
    }
}
