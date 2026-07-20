package com.bidmyhobby.hobbyaibackend.profiles;

import org.springframework.stereotype.Service;

@Service
public class AiProfileSearchService {
    private final ProfileSearchService search;
    private final ProfileRepository profiles;

    public AiProfileSearchService(ProfileSearchService search, ProfileRepository profiles) {
        this.search = search;
        this.profiles = profiles;
    }

    public Profile searchProfile(String query) {
        return search.searchProfiles(query, 1).stream().findFirst().orElseGet(profiles::getRandomProfile);
    }
}
