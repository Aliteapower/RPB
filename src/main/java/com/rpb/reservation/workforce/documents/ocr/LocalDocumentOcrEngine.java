package com.rpb.reservation.workforce.documents.ocr;

import com.rpb.reservation.workforce.documents.DocumentDtos.FileData;
import com.rpb.reservation.workforce.domain.WorkforceException;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;

/** Isolated local processes with a shared deadline, bounded output and no document logging. */
@Component
public class LocalDocumentOcrEngine implements DocumentOcrEngine {
    private static final int MAX_RENDER_BYTES=32*1024*1024;
    private final List<String> tesseract,pdftoppm;
    private final Path temporaryRoot;
    private final Duration timeout;
    private final Semaphore slots;
    private final int maxOutput;
    public LocalDocumentOcrEngine() {
        this(List.of(tesseractCommand()),List.of(command("WORKFORCE_OCR_PDFTOPPM_CMD","pdftoppm")),
             Path.of(System.getProperty("java.io.tmpdir")),Duration.ofSeconds(59),2,64*1024);
    }
    LocalDocumentOcrEngine(List<String> tesseract,List<String> pdftoppm,Path temporaryRoot,Duration timeout,int concurrency,int maxOutput) {
        this.tesseract=List.copyOf(tesseract);this.pdftoppm=List.copyOf(pdftoppm);this.temporaryRoot=temporaryRoot;
        this.timeout=timeout;this.slots=new Semaphore(concurrency);this.maxOutput=maxOutput;
    }
    private static String command(String variable,String fallback) {
        String value=System.getenv(variable);return value==null||value.isBlank()?fallback:value;
    }
    private static String tesseractCommand() {
        String configured=System.getenv("WORKFORCE_OCR_TESSERACT_CMD");
        if(configured!=null&&!configured.isBlank())return configured;
        Path windows=Path.of("C:/Program Files/Tesseract-OCR/tesseract.exe");
        return Files.isExecutable(windows)?windows.toString():"tesseract";
    }
    @Override public String recognize(FileData file) {
        String extension=switch(file.contentType()){
            case "image/jpeg"->"jpg";case "image/png"->"png";case "image/webp"->"webp";case "application/pdf"->"pdf";
            default->throw new WorkforceException("WORKFORCE_DOCUMENT_OCR_UNSUPPORTED",400);
        };
        if(!slots.tryAcquire())throw new WorkforceException("WORKFORCE_DOCUMENT_OCR_BUSY",429);
        Path directory=null;
        long deadline=System.nanoTime()+timeout.toNanos();
        try {
            directory=Files.createTempDirectory(temporaryRoot,"rpb-document-ocr-");
            Path input=directory.resolve("input."+extension);Files.write(input,file.bytes());
            List<Path> images;
            if(extension.equals("pdf")) {
                Path prefix=directory.resolve("page");
                run(arguments(pdftoppm,"-f","1","-l","2","-scale-to","2200","-png",input.toString(),prefix.toString()),directory,deadline);
                try(var files=Files.list(directory)) {
                    images=files.filter(p->p.getFileName().toString().matches("page-\\d+\\.png")).sorted().toList();
                }
                if(images.isEmpty()||images.size()>2)throw unavailable();
            } else images=List.of(input);
            StringBuilder result=new StringBuilder();
            for(Path image:images) {
                String text=run(arguments(tesseract,image.toString(),"stdout","-l","eng+chi_sim","--oem","1","--psm","6"),directory,deadline);
                if(result.length()+text.length()>maxOutput)throw unavailable();
                result.append(text).append('\n');
            }
            return result.toString();
        }catch(IOException e){throw unavailable();}
        finally {
            if(directory!=null)clean(directory);
            slots.release();
        }
    }
    private List<String> arguments(List<String> prefix,String...arguments) {
        List<String> command=new ArrayList<>(prefix);command.addAll(List.of(arguments));return command;
    }
    private String run(List<String> command,Path directory,long deadline)throws IOException {
        if(System.nanoTime()>=deadline)throw unavailable();
        Process process=new ProcessBuilder(command).redirectError(ProcessBuilder.Redirect.DISCARD).start();
        var output=new FutureTask<byte[]>(()->{
            try(InputStream stream=process.getInputStream()) {
                byte[] bytes=stream.readNBytes(maxOutput+1);
                if(bytes.length>maxOutput){process.destroyForcibly();throw unavailable();}
                return bytes;
            }
        });
        Thread reader=Thread.ofVirtual().name("workforce-document-ocr-output").start(output);
        try {
            while(!process.waitFor(50,TimeUnit.MILLISECONDS)) {
                if(System.nanoTime()>=deadline||directorySize(directory)>MAX_RENDER_BYTES)throw unavailable();
            }
            if(process.exitValue()!=0||System.nanoTime()>=deadline||directorySize(directory)>MAX_RENDER_BYTES)throw unavailable();
            long remaining=deadline-System.nanoTime();
            byte[] bytes=output.get(Math.max(1,remaining),TimeUnit.NANOSECONDS);
            return new String(bytes,StandardCharsets.UTF_8);
        } catch(InterruptedException e){Thread.currentThread().interrupt();throw unavailable();}
          catch(ExecutionException|TimeoutException e){throw unavailable();}
        finally {
            process.descendants().forEach(ProcessHandle::destroyForcibly);
            process.destroyForcibly();
            try{process.waitFor(500,TimeUnit.MILLISECONDS);}catch(InterruptedException e){Thread.currentThread().interrupt();}
            try{process.getInputStream().close();}catch(IOException ignored){}
            output.cancel(true);reader.interrupt();
        }
    }
    private long directorySize(Path directory)throws IOException {
        try(var paths=Files.list(directory)) {
            long total=0;
            for(Path path:paths.toList()){total+=Files.size(path);if(total>MAX_RENDER_BYTES)return total;}
            return total;
        }
    }
    private void clean(Path directory) {
        try(var paths=Files.walk(directory)) {
            for(Path path:paths.sorted(Comparator.reverseOrder()).toList())Files.deleteIfExists(path);
        }catch(IOException ignored){/* No paths, document contents or process output enter logs. */}
    }
    private static WorkforceException unavailable(){return new WorkforceException("WORKFORCE_DOCUMENT_OCR_UNAVAILABLE",503);}
}
