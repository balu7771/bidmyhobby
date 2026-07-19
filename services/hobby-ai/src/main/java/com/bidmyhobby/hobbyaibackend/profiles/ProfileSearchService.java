package com.bidmyhobby.hobbyaibackend.profiles;

import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;

@Service
public class ProfileSearchService {
    private final ProfileRepository profiles;

    public ProfileSearchService(ProfileRepository profiles) {
        this.profiles = profiles;
    }

    public List<Profile> searchProfiles(String query, int maxResults) {
        String[] terms = (query == null ? "" : query.toLowerCase(Locale.ROOT)).split("\\s+");
        return profiles.findAll().stream()
                .map(profile -> new Match(profile, score(profile, terms)))
                .filter(match -> match.score() > 0 || query == null || query.isBlank())
                .sorted(Comparator.comparingInt(Match::score).reversed())
                .limit(Math.max(1, Math.min(maxResults, 20)))
                .map(Match::profile)
                .toList();
    }

    private int score(Profile profile, String[] terms) {
        String text = String.join(" ",
                value(profile.getFirstName()), value(profile.getLastName()), value(profile.getBio()),
                value(profile.getMainHobby()), value(profile.getEthnicity()),
                profile.getGender() == null ? "" : profile.getGender().name()).toLowerCase(Locale.ROOT);
        int score = 0;
        for (String term : terms) if (!term.isBlank() && text.contains(term)) score++;
        return score;
    }

    private String value(String value) { return value == null ? "" : value; }
    private record Match(Profile profile, int score) {}
}
