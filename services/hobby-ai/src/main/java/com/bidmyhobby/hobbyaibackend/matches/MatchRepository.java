package com.bidmyhobby.hobbyaibackend.matches;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MatchRepository extends JpaRepository<Match, String> {
}