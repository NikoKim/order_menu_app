package com.navy.identity;

public record PlayerIdentity(String type, Long userId, Long guestId, String key, String displayName) {
    public boolean loggedIn() { return userId != null; }
}
