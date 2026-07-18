package com.bidmyhobby.hobbyaibackend.whatsapp;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface WhatsAppSessionRepository extends JpaRepository<WhatsAppSession, String> {
}
