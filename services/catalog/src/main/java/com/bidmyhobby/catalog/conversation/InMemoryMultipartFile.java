package com.bidmyhobby.catalog.conversation;

import org.springframework.web.multipart.MultipartFile;
import java.io.*;

final class InMemoryMultipartFile implements MultipartFile {
    private final String name;
    private final String contentType;
    private final byte[] bytes;
    InMemoryMultipartFile(String name, String contentType, byte[] bytes) {
        this.name = name;
        this.contentType = contentType;
        this.bytes = bytes;
    }
    public String getName() { return "files"; }
    public String getOriginalFilename() { return name; }
    public String getContentType() { return contentType; }
    public boolean isEmpty() { return bytes.length == 0; }
    public long getSize() { return bytes.length; }
    public byte[] getBytes() { return bytes; }
    public InputStream getInputStream() { return new ByteArrayInputStream(bytes); }
    public void transferTo(File dest) throws IOException {
        try (OutputStream out = new FileOutputStream(dest)) { out.write(bytes); }
    }
}
