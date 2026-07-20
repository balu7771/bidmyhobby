package com.bidmyhobby.hobbyaibackend.profiles;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "profiles")
public class Profile {
    
    @Id
    private String id;
    private String firstName;
    private String lastName;
    private int age;
    private String ethnicity;
    
    @Enumerated(EnumType.STRING)
    private Gender gender;
    
    @Column(length = 1000)
    private String bio;
    private String imageUrl;
    private String mainHobby;
    private String whatsappNumber;
    private boolean whatsappNotificationsEnabled;
    private LocalDateTime whatsappConsentAt;
    private String whatsappConsentVersion;
    
    public Profile() {
        // Required by JPA
    }
    
    public Profile(String id, String firstName, String lastName, int age, String ethnicity, 
                  Gender gender, String bio, String imageUrl, String mainHobby) {
        this.id = id;
        this.firstName = firstName;
        this.lastName = lastName;
        this.age = age;
        this.ethnicity = ethnicity;
        this.gender = gender;
        this.bio = bio;
        this.imageUrl = imageUrl;
        this.mainHobby = mainHobby;
    }
    
    // Getters
    public String id() { return id; }
    public String firstName() { return firstName; }
    public String lastName() { return lastName; }
    public int age() { return age; }
    public String ethnicity() { return ethnicity; }
    public Gender gender() { return gender; }
    public String bio() { return bio; }
    public String imageUrl() { return imageUrl; }
    public String mainHobby() { return mainHobby; }
    
    // Standard getters and setters for JPA
    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    
    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }
    
    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }
    
    public int getAge() { return age; }
    public void setAge(int age) { this.age = age; }
    
    public String getEthnicity() { return ethnicity; }
    public void setEthnicity(String ethnicity) { this.ethnicity = ethnicity; }
    
    public Gender getGender() { return gender; }
    public void setGender(Gender gender) { this.gender = gender; }
    
    public String getBio() { return bio; }
    public void setBio(String bio) { this.bio = bio; }
    
    public String getImageUrl() { return imageUrl; }
    public void setImageUrl(String imageUrl) { this.imageUrl = imageUrl; }
    
    public String getMainHobby() { return mainHobby; }
    public void setMainHobby(String mainHobby) { this.mainHobby = mainHobby; }
    public String getWhatsappNumber() { return whatsappNumber; }
    public void setWhatsappNumber(String whatsappNumber) { this.whatsappNumber = whatsappNumber; }
    public boolean isWhatsappNotificationsEnabled() { return whatsappNotificationsEnabled; }
    public void setWhatsappNotificationsEnabled(boolean enabled) { this.whatsappNotificationsEnabled = enabled; }
    public LocalDateTime getWhatsappConsentAt() { return whatsappConsentAt; }
    public void setWhatsappConsentAt(LocalDateTime whatsappConsentAt) { this.whatsappConsentAt = whatsappConsentAt; }
    public String getWhatsappConsentVersion() { return whatsappConsentVersion; }
    public void setWhatsappConsentVersion(String whatsappConsentVersion) { this.whatsappConsentVersion = whatsappConsentVersion; }
    
    @Override
    public String toString() {
        return "Profile{" +
                "id='" + id + '\'' +
                ", firstName='" + firstName + '\'' +
                ", lastName='" + lastName + '\'' +
                ", age=" + age +
                ", ethnicity='" + ethnicity + '\'' +
                ", gender=" + gender +
                ", bio='" + bio + '\'' +
                ", imageUrl='" + imageUrl + '\'' +
                ", mainHobby='" + mainHobby + '\'' +
                '}';
    }
}
