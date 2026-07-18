package com.bidmyhobby.hobbyaibackend.profiles;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface ProfileRepository extends JpaRepository<Profile, String> {
    
    @Query(value = "SELECT * FROM profiles ORDER BY RANDOM() LIMIT 1", nativeQuery = true)
    Profile getRandomProfile();
}