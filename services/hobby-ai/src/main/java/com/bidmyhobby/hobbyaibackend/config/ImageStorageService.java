package com.bidmyhobby.hobbyaibackend.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.UUID;

@Service
public class ImageStorageService {

    @Value("${app.storage.images-dir:images}")
    private String imagesDir;
    
    public String storeImage(MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            throw new IOException("Failed to store empty file");
        }
        
        String filename = UUID.randomUUID().toString() + getFileExtension(file.getOriginalFilename());
        Path destinationFile = Paths.get(imagesDir).resolve(filename).normalize().toAbsolutePath();
        
        try (InputStream inputStream = file.getInputStream()) {
            Files.copy(inputStream, destinationFile, StandardCopyOption.REPLACE_EXISTING);
        }
        
        return filename;
    }
    
    public void deleteImage(String filename) {
        try {
            Path file = Paths.get(imagesDir).resolve(filename).normalize().toAbsolutePath();
            Files.deleteIfExists(file);
        } catch (IOException e) {
            System.err.println("Error deleting image: " + e.getMessage());
        }
    }
    
    private String getFileExtension(String filename) {
        if (filename == null || filename.lastIndexOf(".") == -1) {
            return ".jpg";
        }
        return filename.substring(filename.lastIndexOf("."));
    }
}