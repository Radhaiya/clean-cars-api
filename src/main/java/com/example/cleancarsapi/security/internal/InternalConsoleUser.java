package com.example.cleancarsapi.security.internal;

/**
 * The internal console's principal — a whitelisted console user. Deliberately
 * not an {@link com.example.cleancarsapi.security.AuthenticatedUser}: there is no
 * org scoping, no role and no {@code users} row here. Identity is the Firebase
 * uid + verified email; {@code uid} is not a UUID.
 */
public record InternalConsoleUser(String uid, String email, String name) {
}
